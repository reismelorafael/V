use std::fs;
use std::io::Cursor;

use vectra_policy_kernel::{
    crc32c, deterministic_mutate, PipelineConfig, PolicyKernel, Stage, StageEvent, TriadStatus,
};

#[test]
fn golden_crc32c_vectors_are_stable() {
    assert_eq!(crc32c(b""), 0x00000000);
    assert_eq!(crc32c(b"123456789"), 0xE3069283);
    assert_eq!(crc32c(b"The quick brown fox jumps over the lazy dog"), 0x22620404);
}

#[test]
fn deterministic_pipeline_produces_identical_audit_log() {
    let payload = b"vectras-deterministic-policy-layer".repeat(512);
    let cfg = PipelineConfig {
        chunk_size: 4096,
        mutation_xor: 0xA5,
        mutation_stride: 31,
    };
    let kernel = PolicyKernel::new();
    let triad = TriadStatus::default();

    let mut plan_a = Cursor::new(payload.clone());
    let planned_a = kernel.plan(&mut plan_a, &cfg, triad).expect("plan A");

    let mut apply_a_in = Cursor::new(payload.clone());
    let mut apply_a_out = Vec::new();
    let applied_a = kernel
        .apply(&mut apply_a_in, &mut apply_a_out, &cfg, triad)
        .expect("apply A");

    let mut verify_a_in = Cursor::new(apply_a_out.clone());
    kernel
        .verify(&mut verify_a_in, &applied_a, &cfg, triad)
        .expect("verify A");

    let events_a: Vec<StageEvent> = planned_a
        .into_iter()
        .map(|chunk| StageEvent { stage: Stage::Plan, chunk })
        .collect();
    let log_a = "target/test_audit_a.log";
    let _ = fs::remove_file(log_a);
    kernel.write_audit_log(log_a, &events_a).expect("audit A");

    let mut plan_b = Cursor::new(payload.clone());
    let planned_b = kernel.plan(&mut plan_b, &cfg, triad).expect("plan B");
    let events_b: Vec<StageEvent> = planned_b
        .into_iter()
        .map(|chunk| StageEvent { stage: Stage::Plan, chunk })
        .collect();
    let log_b = "target/test_audit_b.log";
    let _ = fs::remove_file(log_b);
    kernel.write_audit_log(log_b, &events_b).expect("audit B");

    let a = fs::read_to_string(log_a).expect("read log A");
    let b = fs::read_to_string(log_b).expect("read log B");
    assert_eq!(a, b);
}

#[test]
fn bitflip_corruption_is_detected_by_verify() {
    let payload = b"bitflip-detection".repeat(1024);
    let cfg = PipelineConfig::default();
    let kernel = PolicyKernel::new();
    let triad = TriadStatus::default();

    let mut apply_in = Cursor::new(payload);
    let mut out = Vec::new();
    let expected = kernel
        .apply(&mut apply_in, &mut out, &cfg, triad)
        .expect("apply should pass");

    out[17] ^= 0x01;

    let mut verify_in = Cursor::new(out);
    let err = kernel.verify(&mut verify_in, &expected, &cfg, triad).unwrap_err();
    let msg = err.to_string();
    assert!(msg.contains("verify_mismatch"), "unexpected error: {msg}");
}

#[test]
fn deterministic_mutation_is_pure_function() {
    let mut data_a = vec![0u8; 128];
    let mut data_b = vec![0u8; 128];
    deterministic_mutate(&mut data_a, 0xAA, 7);
    deterministic_mutate(&mut data_b, 0xAA, 7);
    assert_eq!(data_a, data_b);
}
