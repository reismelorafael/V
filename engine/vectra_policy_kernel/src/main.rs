use std::env;
use std::fs::File;
use std::io::{BufReader, BufWriter, Write};

use vectra_policy_kernel::{PipelineConfig, PolicyKernel, Stage, StageEvent, TriadStatus};

fn usage(bin: &str) {
    eprintln!(
        "Usage: {bin} <input_stream> <mutated_output> <audit_log> [chunk_size] [mutation_xor_hex] [mutation_stride]"
    );
}

fn parse_u8_hex(src: &str) -> Option<u8> {
    let clean = src.trim_start_matches("0x").trim_start_matches("0X");
    u8::from_str_radix(clean, 16).ok()
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 4 {
        usage(&args[0]);
        std::process::exit(2);
    }

    let mut cfg = PipelineConfig::default();
    if let Some(chunk_size) = args.get(4).and_then(|v| v.parse::<usize>().ok()) {
        cfg.chunk_size = chunk_size.max(1);
    }
    if let Some(mask) = args.get(5).and_then(|v| parse_u8_hex(v)) {
        cfg.mutation_xor = mask;
    }
    if let Some(stride) = args.get(6).and_then(|v| v.parse::<usize>().ok()) {
        cfg.mutation_stride = stride;
    }

    let input_path = &args[1];
    let output_path = &args[2];
    let log_path = &args[3];

    let kernel = PolicyKernel::new();
    let triad = TriadStatus::default();

    let mut planner_input = BufReader::new(File::open(input_path).expect("failed to open input stream for PLAN"));
    let planned = kernel.plan(&mut planner_input, &cfg, triad).expect("PLAN stage failed");

    let mut apply_input = BufReader::new(File::open(input_path).expect("failed to open input stream for APPLY"));
    let mut apply_output = BufWriter::new(File::create(output_path).expect("failed to create output stream"));
    let applied = kernel
        .apply(&mut apply_input, &mut apply_output, &cfg, triad)
        .expect("APPLY stage failed");
    apply_output.flush().expect("failed to flush output stream");

    let diff = kernel.diff(&planned, &applied).expect("DIFF stage failed");

    let mut verify_input = BufReader::new(File::open(output_path).expect("failed to open output stream for VERIFY"));
    let verified = kernel
        .verify(&mut verify_input, &applied, &cfg, triad)
        .expect("VERIFY stage failed");

    let mut events = Vec::new();
    for chunk in planned {
        events.push(StageEvent { stage: Stage::Plan, chunk });
    }
    for (idx, chunk) in applied.iter().enumerate() {
        let mut diff_chunk = chunk.clone();
        if let Some(d) = diff.get(idx) {
            diff_chunk.flags.miss = !d.changed;
            diff_chunk.flags.bad_event = !d.changed;
        }
        events.push(StageEvent { stage: Stage::Diff, chunk: diff_chunk });
        events.push(StageEvent { stage: Stage::Apply, chunk: chunk.clone() });
    }
    for chunk in verified {
        events.push(StageEvent { stage: Stage::Verify, chunk });
    }

    kernel
        .write_audit_log(log_path, &events)
        .expect("AUDIT stage failed");

    println!(
        "deterministic_vm_mutation_done stages=PLAN,DIFF,APPLY,VERIFY,AUDIT chunks={} log={} out={}",
        applied.len(),
        log_path,
        output_path
    );
}
