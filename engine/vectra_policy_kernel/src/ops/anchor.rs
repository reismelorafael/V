use crate::{arg_or_empty, AnchorAddr, DeterministicOp, Output};

pub(crate) struct AnchorOp;

pub(crate) static ANCHOR_OP: AnchorOp = AnchorOp;

impl DeterministicOp for AnchorOp {
    fn canonize(&self, args: &[String]) -> Vec<String> {
        vec![arg_or_empty(args, 0).trim().to_string()]
    }

    fn execute(&self, key_args: &[String]) -> Output {
        let _ = arg_or_empty(key_args, 0);
        Output::Anchor(AnchorAddr {
            dev: 0,
            block: 0,
            page: 0,
        })
    }

    fn op_code(&self) -> &'static str {
        "anchor"
    }
}
