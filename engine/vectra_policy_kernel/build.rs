fn main() {
    let mut build = cc::Build::new();
    build
        .define("RMR_JNI_BUILD", "1")
        .define("RMR_ENABLE_POLICY_MODULE", "1")
        .include("../rmr/include")
        .file("../rmr/src/rmr_unified_kernel.c")
        .file("../rmr/src/rmr_hw_detect.c")
        .file("../rmr/src/rmr_cycles.c")
        .file("../rmr/src/rmr_ll_ops.c")
        .file("../rmr/src/rmr_corelib.c")
        .file("../rmr/src/rmr_math_fabric.c")
        .file("../rmr/src/bitraf.c")
        .file("../rmr/src/bitomega.c")
        .file("../rmr/src/rmr_zipraf_core.c")
        .file("../rmr/src/rmr_policy_kernel.c")
        .warnings(false)
        .compile("rmr_unified_kernel");

    println!("cargo:rerun-if-changed=../rmr/include/rmr_unified_kernel.h");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_unified_kernel.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_hw_detect.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_cycles.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_ll_ops.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_corelib.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_math_fabric.c");
    println!("cargo:rerun-if-changed=../rmr/src/bitraf.c");
    println!("cargo:rerun-if-changed=../rmr/src/bitomega.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_zipraf_core.c");
    println!("cargo:rerun-if-changed=../rmr/src/rmr_policy_kernel.c");
}
