use std::collections::BTreeMap;
use std::fs;
use std::io::Cursor;

use vectra_policy_kernel::{
    canonize, commit_tick, crc32c, deterministic_mutate, entropy_hint, entropy_milli, exec_bucket,
    resolve_op_by_code, AnchorAddr, Event, Key, LogEntry, Op, Output, PipelineConfig, PolicyKernel,
    Stage, StageEvent, TriadStatus,
};

#[derive(Default)]
struct SchedulerState {
    anchors: BTreeMap<AnchorAddr, u8>,
}

impl SchedulerState {
    fn replay_log(&mut self, log: Vec<(u64, Output)>) {
        for (_, out) in log {
            if let Output::Anchor(anchor) = out {
                let count = self.anchors.entry(anchor).or_default();
                *count = count.saturating_add(1);
            }
        }
    }
}

#[test]
fn golden_crc32c_vectors_are_stable() {
    assert_eq!(crc32c(b""), 0x00000000);
    assert_eq!(crc32c(b"123456789"), 0xE3069283);
    assert_eq!(
        crc32c(b"The quick brown fox jumps over the lazy dog"),
        0x22620404
    );
}

#[test]
fn entropy_metric_is_bit_stable() {
    let zero = vec![0u8; 4096];
    let ramp = (0..=255u8).cycle().take(4096).collect::<Vec<_>>();
    let striped = (0..4096)
        .map(|i| if (i & 1) == 0 { 0x00 } else { 0xFF })
        .collect::<Vec<_>>();

    assert_eq!(entropy_milli(&[]), 0);
    assert_eq!(entropy_milli(&zero), 0);
    assert_eq!(entropy_milli(&striped), 1000);
    assert_eq!(entropy_milli(&ramp), 8000);

    assert!(!entropy_hint(&zero));
    assert!(entropy_hint(&ramp));
}

#[test]
fn entropy_metric_does_not_plateau_past_u16_frequency_limit() {
    let long_biased = [
        vec![0u8; 70_000],
        vec![1u8; 40_000],
        vec![2u8; 20_000],
        vec![3u8; 10_000],
    ]
    .concat();

    let mut with_extra_symbol = long_biased.clone();
    with_extra_symbol.extend(std::iter::repeat_n(4u8, 100));

    let baseline = entropy_milli(&long_biased);
    let shifted = entropy_milli(&with_extra_symbol);
    assert!(
        shifted > baseline,
        "entropy should increase when adding a new symbol above 65535-length regimes; baseline={baseline}, shifted={shifted}"
    );
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

    let diff_a = kernel.diff(&planned_a, &applied_a).expect("diff A");
    assert!(
        diff_a.iter().all(|d| d.changed),
        "PLAN must compare baseline chunks while APPLY compares mutated chunks"
    );

    let mut verify_a_in = Cursor::new(apply_a_out.clone());
    kernel
        .verify(&mut verify_a_in, &applied_a, &cfg, triad)
        .expect("verify A");

    let events_a: Vec<StageEvent> = planned_a
        .into_iter()
        .map(|chunk| StageEvent {
            stage: Stage::Plan,
            chunk,
            anchor: None,
        })
        .collect();
    let log_a = "target/test_audit_a.log";
    let _ = fs::remove_file(log_a);
    kernel.write_audit_log(log_a, &events_a).expect("audit A");

    let mut plan_b = Cursor::new(payload.clone());
    let planned_b = kernel.plan(&mut plan_b, &cfg, triad).expect("plan B");
    let diff_b = kernel.diff(&planned_b, &applied_a).expect("diff B");
    assert!(
        diff_b.iter().all(|d| d.changed),
        "every chunk should be marked changed when compared against APPLY output"
    );
    let events_b: Vec<StageEvent> = planned_b
        .into_iter()
        .map(|chunk| StageEvent {
            stage: Stage::Plan,
            chunk,
            anchor: None,
        })
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
    let err = kernel
        .verify(&mut verify_in, &expected, &cfg, triad)
        .unwrap_err();
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

#[test]
fn canonize_produces_stable_forms_per_operation() {
    let trim = canonize(Op::TrimWs, &["  abc  ".to_string()], None);
    assert_eq!(
        trim,
        Key {
            op: Op::TrimWs,
            args: vec!["abc".to_string()],
            anchor: None,
            canon: "trim_ws|abc|-".to_string(),
        }
    );

    let len = canonize(Op::Len, &["  abc  ".to_string()], None);
    assert_eq!(
        len,
        Key {
            op: Op::Len,
            args: vec!["  abc  ".to_string()],
            anchor: None,
            canon: "len|  abc  |-".to_string(),
        }
    );

    let anchor = AnchorAddr {
        dev: 2,
        block: 15,
        page: 3,
    };
    let replace = canonize(
        Op::ReplaceChar,
        &["aba".to_string(), "ab".to_string(), "xy".to_string()],
        Some(anchor),
    );
    assert_eq!(
        replace,
        Key {
            op: Op::ReplaceChar,
            args: vec!["aba".to_string(), "a".to_string(), "x".to_string()],
            anchor: Some(anchor),
            canon: "replace_char|aba\u{1f}a\u{1f}x|2:15:3".to_string(),
        }
    );
}

#[test]
fn commit_tick_groups_sorts_and_fans_out_outputs() {
    let events = vec![
        Event {
            id: 7,
            op: Op::Len,
            args: vec!["hello".to_string()],
            anchor: None,
        },
        Event {
            id: 2,
            op: Op::TrimWs,
            args: vec!["  x  ".to_string()],
            anchor: None,
        },
        Event {
            id: 3,
            op: Op::TrimWs,
            args: vec!["x".to_string()],
            anchor: None,
        },
    ];

    let committed = commit_tick(1, &events);
    assert_eq!(committed.len(), 3);
    assert_eq!(committed[0], (2, Output::Text("x".to_string())));
    assert_eq!(committed[1], (3, Output::Text("x".to_string())));
    assert_eq!(committed[2], (7, Output::Number(5)));
}

#[test]
fn exec_bucket_executes_once_and_reuses_output() {
    let anchor = AnchorAddr {
        dev: 7,
        block: 99,
        page: 1,
    };
    let key = Key {
        op: Op::AnchorMark,
        args: vec!["anchor-1".to_string()],
        anchor: Some(anchor),
        canon: "anchor|anchor-1|7:99:1".to_string(),
    };
    let bucket = vec![
        Event {
            id: 10,
            op: Op::AnchorMark,
            args: vec!["anchor-1".to_string()],
            anchor: Some(anchor),
        },
        Event {
            id: 11,
            op: Op::AnchorMark,
            args: vec!["anchor-1".to_string()],
            anchor: Some(anchor),
        },
    ];

    let out = exec_bucket(&key, &bucket);
    assert_eq!(out.len(), 2);
    assert_eq!(out[0], (10, Output::Anchor(anchor)));
    assert_eq!(out[1], (11, Output::Anchor(anchor)));
}

#[test]
fn scheduler_replay_materializes_anchor_trust() {
    let anchor = AnchorAddr {
        dev: 1,
        block: 42,
        page: 9,
    };
    let mut state = SchedulerState::default();
    state.replay_log(vec![
        (1, Output::Text("ignore".to_string())),
        (2, Output::Anchor(anchor)),
        (3, Output::Anchor(anchor)),
    ]);
    assert_eq!(state.anchors.get(&anchor), Some(&2u8));
}

#[test]
fn rollback_is_idempotent() {
    let mut kernel = PolicyKernel::new();
    assert_eq!(kernel.seq(), 0);

    kernel.checkpoint();
    kernel
        .apply_log_entry(LogEntry {
            seq: 0,
            op: Op::SetFocus,
            args: vec!["main".to_string()],
            output: Output::Focus("main".to_string()),
        })
        .expect("focus entry");
    kernel
        .apply_log_entry(LogEntry {
            seq: 1,
            op: Op::AnchorMark,
            args: vec!["1:1:1".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 1,
                block: 1,
                page: 1,
            }),
        })
        .expect("anchor entry");

    assert_eq!(kernel.focused(), Some("main"));
    assert_eq!(
        kernel.anchors(),
        [AnchorAddr {
            dev: 1,
            block: 1,
            page: 1
        }]
    );
    assert_eq!(kernel.seq(), 2);
    assert_eq!(kernel.log().len(), 2);

    assert!(kernel.rollback());
    assert_eq!(kernel.focused(), None);
    assert!(kernel.anchors().is_empty());
    assert_eq!(kernel.seq(), 0);
    assert!(kernel.log().is_empty());

    assert!(!kernel.rollback());
    assert_eq!(kernel.focused(), None);
    assert!(kernel.anchors().is_empty());
    assert_eq!(kernel.seq(), 0);
    assert!(kernel.log().is_empty());
}

#[test]
fn log_replay_is_reproducible() {
    let mut kernel_a = PolicyKernel::new();
    let entries = vec![
        LogEntry {
            seq: 0,
            op: Op::SetFocus,
            args: vec!["left".to_string()],
            output: Output::Focus("left".to_string()),
        },
        LogEntry {
            seq: 1,
            op: Op::AnchorMark,
            args: vec!["1:10:1".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 1,
                block: 10,
                page: 1,
            }),
        },
        LogEntry {
            seq: 2,
            op: Op::AnchorMark,
            args: vec!["1:11:1".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 1,
                block: 11,
                page: 1,
            }),
        },
    ];

    for entry in &entries {
        kernel_a
            .apply_log_entry(entry.clone())
            .expect("append on kernel a");
    }

    let mut kernel_b = PolicyKernel::new();
    for entry in kernel_a.log() {
        kernel_b
            .apply_log_entry(entry.clone())
            .expect("replay on kernel b");
    }

    assert_eq!(kernel_b.focused(), kernel_a.focused());
    assert_eq!(kernel_b.anchors(), kernel_a.anchors());
    assert_eq!(kernel_b.seq(), kernel_a.seq());
    assert_eq!(kernel_b.log(), kernel_a.log());
}

#[test]
fn seq_stays_consistent_through_checkpoint_and_rollback() {
    let mut kernel = PolicyKernel::new();

    kernel
        .apply_log_entry(LogEntry {
            seq: 0,
            op: Op::SetFocus,
            args: vec!["root".to_string()],
            output: Output::Focus("root".to_string()),
        })
        .expect("entry 0");
    assert_eq!(kernel.seq(), 1);

    kernel.checkpoint();
    kernel
        .apply_log_entry(LogEntry {
            seq: 1,
            op: Op::AnchorMark,
            args: vec!["2:1:1".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 2,
                block: 1,
                page: 1,
            }),
        })
        .expect("entry 1");
    assert_eq!(kernel.seq(), 2);

    assert!(kernel.rollback());
    assert_eq!(kernel.seq(), 1);
    assert_eq!(kernel.focused(), Some("root"));
    assert!(kernel.anchors().is_empty());

    kernel
        .apply_log_entry(LogEntry {
            seq: 1,
            op: Op::AnchorMark,
            args: vec!["2:2:1".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 2,
                block: 2,
                page: 1,
            }),
        })
        .expect("entry 1 replayed after rollback");
    assert_eq!(kernel.seq(), 2);

    let seq_err = kernel
        .apply_log_entry(LogEntry {
            seq: 5,
            op: Op::AnchorMark,
            args: vec!["9:9:9".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 9,
                block: 9,
                page: 9,
            }),
        })
        .expect_err("must reject unexpected seq");
    assert!(seq_err
        .to_string()
        .contains("policy_violation=log sequence mismatch"));
}

#[test]
fn anchor_mark_rejects_conflicting_args_and_output() {
    let mut kernel = PolicyKernel::new();
    let err = kernel
        .apply_log_entry(LogEntry {
            seq: 0,
            op: Op::AnchorMark,
            args: vec!["1:2:3".to_string()],
            output: Output::Anchor(AnchorAddr {
                dev: 1,
                block: 2,
                page: 4,
            }),
        })
        .expect_err("must reject mismatched anchor output");

    assert!(err
        .to_string()
        .contains("policy_violation=anchor args/output mismatch"));
}

#[test]
fn plugin_registry_is_deterministic_for_same_input() {
    let plugins = ["trim_ws", "len", "replace_char", "focus", "anchor"];
    let args = vec!["  alpha  ".to_string(), "a".to_string(), "z".to_string()];

    for op_code in plugins {
        let plugin = resolve_op_by_code(op_code).expect("plugin must exist");
        let key_a = plugin.canonize(&args);
        let key_b = plugin.canonize(&args);
        assert_eq!(key_a, key_b, "canonize must be deterministic for {op_code}");

        let out_a = plugin.execute(&key_a);
        let out_b = plugin.execute(&key_b);
        assert_eq!(out_a, out_b, "execute must be deterministic for {op_code}");
        assert_eq!(plugin.op_code(), op_code, "registry op_code must be stable");
    }
}

#[test]
fn commit_tick_uses_total_stable_ordering_by_op_code() {
    let events = vec![
        Event {
            id: 40,
            op: Op::TrimWs,
            args: vec!["  z  ".to_string()],
            anchor: None,
        },
        Event {
            id: 10,
            op: Op::AnchorMark,
            args: vec!["z".to_string()],
            anchor: Some(AnchorAddr {
                dev: 3,
                block: 33,
                page: 7,
            }),
        },
        Event {
            id: 20,
            op: Op::Len,
            args: vec!["z".to_string()],
            anchor: None,
        },
        Event {
            id: 30,
            op: Op::SetFocus,
            args: vec!["  z  ".to_string()],
            anchor: None,
        },
    ];

    let committed = commit_tick(1, &events);
    assert_eq!(committed.len(), 4);
    assert_eq!(
        committed[0],
        (
            10,
            Output::Anchor(AnchorAddr {
                dev: 3,
                block: 33,
                page: 7
            })
        )
    );
    assert_eq!(committed[1], (20, Output::Number(1)));
    assert_eq!(committed[2], (30, Output::Focus("z".to_string())));
    assert_eq!(committed[3], (40, Output::Text("z".to_string())));
}

#[test]
fn commit_tick_is_deterministic_across_repeated_runs() {
    let events = vec![
        Event {
            id: 91,
            op: Op::AnchorMark,
            args: vec!["anchor-A".to_string()],
            anchor: Some(AnchorAddr {
                dev: 1,
                block: 2,
                page: 3,
            }),
        },
        Event {
            id: 12,
            op: Op::TrimWs,
            args: vec!["  same  ".to_string()],
            anchor: None,
        },
        Event {
            id: 77,
            op: Op::TrimWs,
            args: vec!["same".to_string()],
            anchor: None,
        },
        Event {
            id: 35,
            op: Op::AnchorMark,
            args: vec!["anchor-B".to_string()],
            anchor: Some(AnchorAddr {
                dev: 1,
                block: 2,
                page: 4,
            }),
        },
        Event {
            id: 56,
            op: Op::ReplaceChar,
            args: vec!["aba".to_string(), "a".to_string(), "x".to_string()],
            anchor: Some(AnchorAddr {
                dev: 9,
                block: 8,
                page: 7,
            }),
        },
    ];

    let committed_a = commit_tick(1, &events);
    let committed_b = commit_tick(1, &events);
    assert_eq!(committed_a, committed_b);
}
