use std::collections::BTreeMap;
use std::fmt::{Display, Formatter};
use std::fs::OpenOptions;
use std::io::{self, BufWriter, Read, Write};
use std::path::Path;

pub const DEFAULT_CHUNK_SIZE: usize = 4096;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Stage {
    Plan,
    Diff,
    Apply,
    Verify,
    Audit,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RouteTarget {
    Cpu,
    Ram,
    Disk,
    Fallback,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct ChunkFlags {
    pub bad_event: bool,
    pub miss: bool,
    pub temp_hint: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ChunkRecord {
    pub sequence: u64,
    pub offset: u64,
    pub len: usize,
    pub crc32c: u32,
    pub optional_hash: u64,
    pub entropy_milli: u16,
    pub flags: ChunkFlags,
    pub route_id: u16,
    pub route_target: RouteTarget,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StageEvent {
    pub stage: Stage,
    pub chunk: ChunkRecord,
}


#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DiffRecord {
    pub sequence: u64,
    pub before_crc32c: u32,
    pub after_crc32c: u32,
    pub changed: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TriadStatus {
    pub cpu_ok: bool,
    pub ram_ok: bool,
    pub disk_ok: bool,
}

impl Default for TriadStatus {
    fn default() -> Self {
        Self {
            cpu_ok: true,
            ram_ok: true,
            disk_ok: true,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PipelineConfig {
    pub chunk_size: usize,
    pub mutation_xor: u8,
    pub mutation_stride: usize,
}

impl Default for PipelineConfig {
    fn default() -> Self {
        Self {
            chunk_size: DEFAULT_CHUNK_SIZE,
            mutation_xor: 0xA5,
            mutation_stride: 31,
        }
    }
}

#[derive(Debug)]
pub enum KernelError {
    Io(io::Error),
    PolicyViolation(&'static str),
    VerifyMismatch { sequence: u64, expected: u32, got: u32 },
}

impl Display for KernelError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            KernelError::Io(err) => write!(f, "io_error={err}"),
            KernelError::PolicyViolation(msg) => write!(f, "policy_violation={msg}"),
            KernelError::VerifyMismatch { sequence, expected, got } => {
                write!(f, "verify_mismatch seq={sequence} expected={expected:#010x} got={got:#010x}")
            }
        }
    }
}

impl std::error::Error for KernelError {}

impl From<io::Error> for KernelError {
    fn from(value: io::Error) -> Self {
        KernelError::Io(value)
    }
}

#[derive(Default)]
pub struct RouteTable {
    routes: BTreeMap<u16, RouteTarget>,
}

impl RouteTable {
    pub fn new_default() -> Self {
        let mut routes = BTreeMap::new();
        routes.insert(1, RouteTarget::Cpu);
        routes.insert(2, RouteTarget::Ram);
        routes.insert(3, RouteTarget::Disk);
        routes.insert(255, RouteTarget::Fallback);
        Self { routes }
    }

    pub fn resolve(&self, route_id: u16) -> RouteTarget {
        self.routes.get(&route_id).copied().unwrap_or(RouteTarget::Fallback)
    }

    pub fn pick_route(&self, status: TriadStatus, sequence: u64) -> (u16, RouteTarget, ChunkFlags) {
        let mut flags = ChunkFlags::default();
        let mut available = Vec::new();
        if status.cpu_ok {
            available.push((1u16, RouteTarget::Cpu));
        }
        if status.ram_ok {
            available.push((2u16, RouteTarget::Ram));
        }
        if status.disk_ok {
            available.push((3u16, RouteTarget::Disk));
        }

        if available.len() >= 2 {
            let pick = (sequence as usize) % available.len();
            let (route_id, target) = available[pick];
            return (route_id, target, flags);
        }

        flags.bad_event = true;
        flags.miss = available.is_empty();
        (255, RouteTarget::Fallback, flags)
    }
}

pub struct PolicyKernel {
    route_table: RouteTable,
    allowed_stages: [Stage; 5],
}

impl PolicyKernel {
    pub fn new() -> Self {
        Self {
            route_table: RouteTable::new_default(),
            allowed_stages: [Stage::Plan, Stage::Diff, Stage::Apply, Stage::Verify, Stage::Audit],
        }
    }

    fn ensure_stage_allowed(&self, stage: Stage) -> Result<(), KernelError> {
        if self.allowed_stages.contains(&stage) {
            Ok(())
        } else {
            Err(KernelError::PolicyViolation("stage not allowed"))
        }
    }

    pub fn plan<R: Read>(
        &self,
        reader: &mut R,
        config: &PipelineConfig,
        triad_status: TriadStatus,
    ) -> Result<Vec<ChunkRecord>, KernelError> {
        self.ensure_stage_allowed(Stage::Plan)?;
        stream_chunks(reader, config.chunk_size, triad_status, &self.route_table, false, config)
    }


    pub fn diff(&self, before: &[ChunkRecord], after: &[ChunkRecord]) -> Result<Vec<DiffRecord>, KernelError> {
        self.ensure_stage_allowed(Stage::Diff)?;
        if before.len() != after.len() {
            return Err(KernelError::PolicyViolation("diff chunk length mismatch"));
        }
        let mut diff = Vec::with_capacity(before.len());
        for (b, a) in before.iter().zip(after.iter()) {
            diff.push(DiffRecord {
                sequence: b.sequence,
                before_crc32c: b.crc32c,
                after_crc32c: a.crc32c,
                changed: b.crc32c != a.crc32c,
            });
        }
        Ok(diff)
    }

    pub fn apply<R: Read, W: Write>(
        &self,
        reader: &mut R,
        writer: &mut W,
        config: &PipelineConfig,
        triad_status: TriadStatus,
    ) -> Result<Vec<ChunkRecord>, KernelError> {
        self.ensure_stage_allowed(Stage::Apply)?;
        let mut sequence = 0u64;
        let mut offset = 0u64;
        let mut out = Vec::new();
        let mut buffer = vec![0u8; config.chunk_size];

        loop {
            let read_len = reader.read(&mut buffer)?;
            if read_len == 0 {
                break;
            }

            let mut chunk = buffer[..read_len].to_vec();
            deterministic_mutate(&mut chunk, config.mutation_xor, config.mutation_stride);
            writer.write_all(&chunk)?;

            let (route_id, route_target, route_flags) = self.route_table.pick_route(triad_status, sequence);
            let flags = ChunkFlags {
                bad_event: route_flags.bad_event,
                miss: route_flags.miss,
                temp_hint: entropy_hint(&chunk),
            };

            out.push(ChunkRecord {
                sequence,
                offset,
                len: read_len,
                crc32c: crc32c(&chunk),
                optional_hash: fnv1a64(&chunk),
                entropy_milli: entropy_milli(&chunk),
                flags,
                route_id,
                route_target,
            });

            sequence += 1;
            offset += read_len as u64;
        }

        Ok(out)
    }

    pub fn verify<R: Read>(
        &self,
        reader: &mut R,
        expected: &[ChunkRecord],
        config: &PipelineConfig,
        triad_status: TriadStatus,
    ) -> Result<Vec<ChunkRecord>, KernelError> {
        self.ensure_stage_allowed(Stage::Verify)?;
        let observed = stream_chunks(reader, config.chunk_size, triad_status, &self.route_table, true, config)?;
        for (exp, got) in expected.iter().zip(observed.iter()) {
            if exp.crc32c != got.crc32c {
                return Err(KernelError::VerifyMismatch {
                    sequence: got.sequence,
                    expected: exp.crc32c,
                    got: got.crc32c,
                });
            }
        }
        if expected.len() != observed.len() {
            return Err(KernelError::PolicyViolation("verify chunk length mismatch"));
        }
        Ok(observed)
    }

    pub fn write_audit_log<P: AsRef<Path>>(&self, path: P, events: &[StageEvent]) -> Result<(), KernelError> {
        self.ensure_stage_allowed(Stage::Audit)?;
        let file = OpenOptions::new().create(true).append(true).open(path)?;
        let mut writer = BufWriter::new(file);
        for event in events {
            let line = serialize_event(event);
            writer.write_all(line.as_bytes())?;
            writer.write_all(b"\n")?;
        }
        writer.flush()?;
        Ok(())
    }
}

fn stream_chunks<R: Read>(
    reader: &mut R,
    chunk_size: usize,
    triad_status: TriadStatus,
    route_table: &RouteTable,
    already_mutated: bool,
    config: &PipelineConfig,
) -> Result<Vec<ChunkRecord>, KernelError> {
    let mut sequence = 0u64;
    let mut offset = 0u64;
    let mut out = Vec::new();
    let mut buffer = vec![0u8; chunk_size];

    loop {
        let read_len = reader.read(&mut buffer)?;
        if read_len == 0 {
            break;
        }

        let mut chunk = buffer[..read_len].to_vec();
        if !already_mutated {
            deterministic_mutate(&mut chunk, config.mutation_xor, config.mutation_stride);
        }
        let (route_id, route_target, route_flags) = route_table.pick_route(triad_status, sequence);
        let flags = ChunkFlags {
            bad_event: route_flags.bad_event,
            miss: route_flags.miss,
            temp_hint: entropy_hint(&chunk),
        };

        out.push(ChunkRecord {
            sequence,
            offset,
            len: read_len,
            crc32c: crc32c(&chunk),
            optional_hash: fnv1a64(&chunk),
            entropy_milli: entropy_milli(&chunk),
            flags,
            route_id,
            route_target,
        });

        sequence += 1;
        offset += read_len as u64;
    }

    Ok(out)
}

pub fn deterministic_mutate(data: &mut [u8], mask: u8, stride: usize) {
    if stride == 0 {
        return;
    }
    for i in (0..data.len()).step_by(stride) {
        data[i] ^= mask;
    }
}

pub fn serialize_event(event: &StageEvent) -> String {
    format!(
        "{{\"stage\":\"{}\",\"seq\":{},\"off\":{},\"len\":{},\"crc32c\":\"{:08x}\",\"hash64\":\"{:016x}\",\"entropy_milli\":{},\"flags\":{{\"bad_event\":{},\"miss\":{},\"temp_hint\":{}}},\"route_id\":{},\"route_target\":\"{}\"}}",
        stage_label(event.stage),
        event.chunk.sequence,
        event.chunk.offset,
        event.chunk.len,
        event.chunk.crc32c,
        event.chunk.optional_hash,
        event.chunk.entropy_milli,
        event.chunk.flags.bad_event,
        event.chunk.flags.miss,
        event.chunk.flags.temp_hint,
        event.chunk.route_id,
        route_label(event.chunk.route_target)
    )
}

fn stage_label(stage: Stage) -> &'static str {
    match stage {
        Stage::Plan => "PLAN",
        Stage::Diff => "DIFF",
        Stage::Apply => "APPLY",
        Stage::Verify => "VERIFY",
        Stage::Audit => "AUDIT",
    }
}

fn route_label(target: RouteTarget) -> &'static str {
    match target {
        RouteTarget::Cpu => "CPU",
        RouteTarget::Ram => "RAM",
        RouteTarget::Disk => "DISK",
        RouteTarget::Fallback => "FALLBACK",
    }
}

pub fn crc32c(data: &[u8]) -> u32 {
    let mut crc = 0xFFFF_FFFFu32;
    for &byte in data {
        crc ^= byte as u32;
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg();
            crc = (crc >> 1) ^ (0x82F63B78 & mask);
        }
    }
    !crc
}

pub fn fnv1a64(data: &[u8]) -> u64 {
    let mut hash = 0xcbf29ce484222325u64;
    for &b in data {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

pub fn entropy_milli(data: &[u8]) -> u16 {
    if data.is_empty() {
        return 0;
    }
    let mut freq = [0usize; 256];
    for &b in data {
        freq[b as usize] += 1;
    }
    let len = data.len() as f64;
    let mut entropy = 0.0f64;
    for count in freq {
        if count == 0 {
            continue;
        }
        let p = count as f64 / len;
        entropy -= p * p.log2();
    }
    let milli = (entropy * 1000.0).round();
    milli.clamp(0.0, 16000.0) as u16
}

pub fn entropy_hint(data: &[u8]) -> bool {
    entropy_milli(data) > 7800
}
