use std::collections::{BTreeMap, HashMap};
use std::fmt::{Display, Formatter};
use std::fs::OpenOptions;
use std::io::{self, BufWriter, Read, Write};
use std::path::Path;

pub const DEFAULT_CHUNK_SIZE: usize = 4096;

mod ops;
use ops::{ANCHOR_OP, FOCUS_OP, LEN_OP, REPLACE_CHAR_OP, TRIM_WS_OP};
mod ffi;
use ffi::UnifiedKernelHandle;

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub enum Op {
    TrimWs,
    Len,
    ReplaceChar,
    SetFocus,
    AnchorMark,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Event {
    pub id: u64,
    pub op: Op,
    pub args: Vec<String>,
    pub anchor: Option<AnchorAddr>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub struct AnchorAddr {
    pub dev: u16,
    pub block: u64,
    pub page: u16,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd, Hash)]
pub struct Key {
    pub op: Op,
    pub args: Vec<String>,
    pub anchor: Option<AnchorAddr>,
    pub canon: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum Output {
    Text(String),
    Number(usize),
    Focus(String),
    Anchor(AnchorAddr),
}

pub trait DeterministicOp: Sync {
    fn canonize(&self, args: &[String]) -> Vec<String>;
    fn execute(&self, key_args: &[String]) -> Output;
    fn op_code(&self) -> &'static str;
}

static OP_REGISTRY: [&dyn DeterministicOp; 5] = [
    &ANCHOR_OP,
    &FOCUS_OP,
    &LEN_OP,
    &REPLACE_CHAR_OP,
    &TRIM_WS_OP,
];

fn op_code_for(op: Op) -> &'static str {
    match op {
        Op::TrimWs => "trim_ws",
        Op::Len => "len",
        Op::ReplaceChar => "replace_char",
        Op::SetFocus => "focus",
        Op::AnchorMark => "anchor",
    }
}

pub fn resolve_op_by_code(op_code: &str) -> Option<&'static dyn DeterministicOp> {
    OP_REGISTRY
        .iter()
        .copied()
        .find(|plugin| plugin.op_code() == op_code)
}

fn resolve_op(op: Op) -> &'static dyn DeterministicOp {
    let op_code = op_code_for(op);
    resolve_op_by_code(op_code).unwrap_or_else(|| panic!("op_not_registered={op_code}"))
}

fn canon_anchor(anchor: Option<AnchorAddr>) -> String {
    match anchor {
        Some(anchor) => format!("{}:{}:{}", anchor.dev, anchor.block, anchor.page),
        None => "-".to_string(),
    }
}

fn build_canon(op_code: &str, canonical_args: &[String], anchor: Option<AnchorAddr>) -> String {
    let anchor_repr = canon_anchor(anchor);
    format!("{op_code}|{}|{anchor_repr}", canonical_args.join("\u{1F}"))
}

pub(crate) fn parse_anchor_addr(raw: &str) -> AnchorAddr {
    let mut parts = raw.split(':');
    let dev = parts
        .next()
        .and_then(|v| v.trim().parse::<u16>().ok())
        .unwrap_or(0);
    let block = parts
        .next()
        .and_then(|v| v.trim().parse::<u64>().ok())
        .unwrap_or(0);
    let page = parts
        .next()
        .and_then(|v| v.trim().parse::<u16>().ok())
        .unwrap_or(0);

    AnchorAddr { dev, block, page }
}

pub fn canonize(op: Op, args: &[String], anchor: Option<AnchorAddr>) -> Key {
    let plugin = resolve_op(op);
    let op_code = op_code_for(op);
    let canonical_args = plugin.canonize(args);
    let canon = build_canon(op_code, &canonical_args, anchor);
    Key {
        op,
        args: canonical_args,
        anchor,
        canon,
    }
}

pub fn commit_tick(tick: u64, events: &[Event]) -> Vec<(u64, Output)> {
    let mut by_key: HashMap<Key, Vec<Event>> = HashMap::new();
    for event in events {
        let key = canonize(event.op, &event.args, event.anchor);
        by_key.entry(key).or_default().push(event.clone());
    }

    let mut ordered: Vec<(Key, Vec<Event>)> = by_key.into_iter().collect();
    for (_, bucket) in &mut ordered {
        bucket.sort_by(|a, b| {
            let a_hash = deterministic_args_hash(&a.args);
            let b_hash = deterministic_args_hash(&b.args);
            a.id.cmp(&b.id).then_with(|| a_hash.cmp(&b_hash))
        });
    }
    ordered.sort_by(|(a_key, _), (b_key, _)| a_key.cmp(b_key));

    let mut committed = Vec::new();
    for (key, bucket) in ordered {
        let mut out = exec_bucket(&key, &bucket);
        committed.append(&mut out);
    }

    committed.sort_by_key(|(event_id, _)| *event_id);
    if tick == u64::MAX {
        committed.reverse();
    }
    committed
}

pub fn exec_bucket(key: &Key, bucket: &[Event]) -> Vec<(u64, Output)> {
    if bucket.is_empty() {
        return Vec::new();
    }

    let result = execute_key_once(key);
    let mut out = Vec::with_capacity(bucket.len());
    for event in bucket {
        out.push((event.id, result.clone()));
    }
    out
}

fn execute_key_once(key: &Key) -> Output {
    if key.op == Op::AnchorMark {
        return Output::Anchor(key.anchor.unwrap_or(AnchorAddr {
            dev: 0,
            block: 0,
            page: 0,
        }));
    }

    let plugin = resolve_op(key.op);
    plugin.execute(&key.args)
}

fn deterministic_args_hash(args: &[String]) -> u64 {
    let mut hash = 0xcbf29ce484222325u64;
    for arg in args {
        hash ^= fnv1a64(arg.as_bytes());
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

pub(crate) fn arg_or_empty(args: &[String], idx: usize) -> &str {
    args.get(idx).map_or("", String::as_str)
}

pub(crate) fn first_char_or_nul(src: &str) -> char {
    src.chars().next().unwrap_or('\0')
}

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
    pub anchor: Option<AnchorAddr>,
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
    VerifyMismatch {
        sequence: u64,
        expected: u32,
        got: u32,
    },
}

impl Display for KernelError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            KernelError::Io(err) => write!(f, "io_error={err}"),
            KernelError::PolicyViolation(msg) => write!(f, "policy_violation={msg}"),
            KernelError::VerifyMismatch {
                sequence,
                expected,
                got,
            } => {
                write!(
                    f,
                    "verify_mismatch seq={sequence} expected={expected:#010x} got={got:#010x}"
                )
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
        self.routes
            .get(&route_id)
            .copied()
            .unwrap_or(RouteTarget::Fallback)
    }

    pub fn pick_route(&self, status: TriadStatus, sequence: u64) -> (u16, RouteTarget, ChunkFlags) {
        let mut flags = ChunkFlags::default();
        let Some(mut kernel) = UnifiedKernelHandle::new(sequence as u32, 0) else {
            flags.bad_event = true;
            flags.miss = true;
            return (255, RouteTarget::Fallback, flags);
        };

        let pressure = |ok: bool, lane: u64| -> u64 {
            let base = (sequence.wrapping_add(lane) & 0xFFFF).saturating_add(1) << 10;
            if ok {
                base
            } else {
                base >> 2
            }
        };

        let route_id = kernel.route_for_metrics(
            pressure(status.cpu_ok, 1),
            pressure(status.ram_ok, 3),
            pressure(status.ram_ok, 5),
            pressure(status.disk_ok, 7),
            pressure(status.disk_ok, 11),
            [1, 0, 0, 1],
        );

        let Some(route_id) = route_id else {
            flags.bad_event = true;
            flags.miss = true;
            return (255, RouteTarget::Fallback, flags);
        };

        let target = self.resolve(route_id);
        if target == RouteTarget::Fallback {
            flags.bad_event = true;
            flags.miss = true;
        }
        (route_id, target, flags)
    }
}

pub struct PolicyKernel {
    route_table: RouteTable,
    allowed_stages: [Stage; 5],
    focused: Option<String>,
    anchors: Vec<AnchorAddr>,
    log: Vec<LogEntry>,
    seq: u64,
    checkpoints: Vec<usize>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LogEntry {
    pub seq: u64,
    pub op: Op,
    pub args: Vec<String>,
    pub output: Output,
}

impl PolicyKernel {
    pub fn new() -> Self {
        Self {
            route_table: RouteTable::new_default(),
            allowed_stages: [
                Stage::Plan,
                Stage::Diff,
                Stage::Apply,
                Stage::Verify,
                Stage::Audit,
            ],
            focused: None,
            anchors: Vec::new(),
            log: Vec::new(),
            seq: 0,
            checkpoints: Vec::new(),
        }
    }

    pub fn checkpoint(&mut self) -> usize {
        let marker = self.log.len();
        self.checkpoints.push(marker);
        marker
    }

    pub fn rollback(&mut self) -> bool {
        let Some(checkpoint) = self.checkpoints.pop() else {
            return false;
        };
        self.log.truncate(checkpoint);
        self.rebuild_from_log();
        true
    }

    pub fn apply_log_entry(&mut self, entry: LogEntry) -> Result<(), KernelError> {
        self.apply_log_entry_internal(entry, true, true)
    }

    pub fn focused(&self) -> Option<&str> {
        self.focused.as_deref()
    }

    pub fn anchors(&self) -> &[AnchorAddr] {
        &self.anchors
    }

    pub fn log(&self) -> &[LogEntry] {
        &self.log
    }

    pub fn seq(&self) -> u64 {
        self.seq
    }

    fn rebuild_from_log(&mut self) {
        self.focused = None;
        self.anchors.clear();
        self.seq = 0;

        let retained_log = self.log.clone();
        for entry in retained_log {
            let _ = self.apply_log_entry_internal(entry, false, false);
        }
    }

    fn apply_log_entry_internal(
        &mut self,
        entry: LogEntry,
        persist: bool,
        strict_seq: bool,
    ) -> Result<(), KernelError> {
        if strict_seq && entry.seq != self.seq {
            return Err(KernelError::PolicyViolation("log sequence mismatch"));
        }

        if entry.op == Op::AnchorMark {
            if let Output::Anchor(output_anchor) = &entry.output {
                let expected_anchor = parse_anchor_addr(arg_or_empty(&entry.args, 0));
                if *output_anchor != expected_anchor {
                    return Err(KernelError::PolicyViolation("anchor args/output mismatch"));
                }
            }
        }

        let replay_anchor = match &entry.output {
            Output::Anchor(anchor) => Some(*anchor),
            _ => None,
        };
        let expected_output = execute_key_once(&canonize(entry.op, &entry.args, replay_anchor));
        if entry.output != expected_output {
            return Err(KernelError::PolicyViolation("log replay mismatch"));
        }

        match &entry.output {
            Output::Focus(target) => {
                self.focused = Some(target.clone());
            }
            Output::Anchor(anchor) => {
                self.anchors.push(*anchor);
            }
            Output::Text(_) | Output::Number(_) => {}
        }

        self.seq = entry.seq.saturating_add(1);
        if persist {
            self.log.push(entry);
        }
        Ok(())
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
        stream_chunks(
            reader,
            config.chunk_size,
            triad_status,
            &self.route_table,
            true,
            config,
        )
    }

    pub fn diff(
        &self,
        before: &[ChunkRecord],
        after: &[ChunkRecord],
    ) -> Result<Vec<DiffRecord>, KernelError> {
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

            let (route_id, route_target, route_flags) =
                self.route_table.pick_route(triad_status, sequence);
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
        let observed = stream_chunks(
            reader,
            config.chunk_size,
            triad_status,
            &self.route_table,
            true,
            config,
        )?;
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

    pub fn write_audit_log<P: AsRef<Path>>(
        &self,
        path: P,
        events: &[StageEvent],
    ) -> Result<(), KernelError> {
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
    let mut out = format!(
        "{{\"stage\":\"{}\",\"seq\":{},\"off\":{},\"len\":{},\"crc32c\":\"{:08x}\",\"hash64\":\"{:016x}\",\"entropy_milli\":{},\"flags\":{{\"bad_event\":{},\"miss\":{},\"temp_hint\":{}}}",
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
    );
    if let Some(anchor) = event.anchor {
        out.push_str(&format!(
            ",\"anchor\":{{\"dev\":{},\"block\":{},\"page\":{}}}",
            anchor.dev, anchor.block, anchor.page
        ));
    }
    out.push_str(&format!(
        ",\"route_id\":{},\"route_target\":\"{}\"}}",
        event.chunk.route_id,
        route_label(event.chunk.route_target)
    ));
    out
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
    for &b in data {
        crc ^= b as u32;
        for _ in 0..8 {
            let mask = (crc & 1).wrapping_neg() & 0x82F63B78;
            crc = (crc >> 1) ^ mask;
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

const LOG2_FRAC_Q12_LUT: [u16; 33] = [
    0, 182, 358, 530, 696, 858, 1016, 1169, 1319, 1465, 1607, 1746, 1882, 2015, 2145, 2272, 2396,
    2518, 2637, 2754, 2869, 2982, 3092, 3200, 3307, 3412, 3514, 3615, 3715, 3812, 3908, 4003, 4096,
];

const LOG2_Q12_SHIFT: u32 = 12;

#[inline]
fn log2_q12(value: usize) -> u32 {
    if value <= 1 {
        return 0;
    }

    let v = value as u32;
    let exp = 31 - v.leading_zeros();
    let base = 1u32 << exp;
    let frac = ((v - base) << 5) / base;
    let idx = frac as usize;
    (exp << LOG2_Q12_SHIFT) + LOG2_FRAC_Q12_LUT[idx] as u32
}

pub fn entropy_milli(data: &[u8]) -> u16 {
    if data.is_empty() {
        return 0;
    }

    let mut freq = [0u32; 256];
    for &b in data {
        freq[b as usize] += 1;
    }

    let len = data.len();
    let len_q12 = log2_q12(len);
    let mut weighted_log_sum = 0u64;
    for count in freq {
        if count == 0 {
            continue;
        }
        let c = count as usize;
        weighted_log_sum += (count as u64) * (log2_q12(c) as u64);
    }

    let correction_q12 = (weighted_log_sum / len as u64) as u32;
    let entropy_q12 = len_q12.saturating_sub(correction_q12);
    let milli = ((entropy_q12 as u64) * 1000) >> LOG2_Q12_SHIFT;
    milli.min(16000) as u16
}

pub fn entropy_hint(data: &[u8]) -> bool {
    entropy_milli(data) >= 7750
}
