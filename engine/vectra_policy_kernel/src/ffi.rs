use std::mem::MaybeUninit;

pub const RMR_UK_OK: i32 = 0;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedCapabilities {
    pub signature: u32,
    pub pointer_bits: u32,
    pub cache_line_bytes: u32,
    pub page_bytes: u32,
    pub feature_mask: u32,
    pub reg_signature_0: u32,
    pub reg_signature_1: u32,
    pub reg_signature_2: u32,
    pub gpio_word_bits: u32,
    pub gpio_pin_stride: u32,
    pub cache_hint_l4: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedConfig {
    pub seed: u32,
    pub arena_bytes: u32,
}


#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_ToroidalAddr7D {
    pub u: u32,
    pub v: u32,
    pub psi: u32,
    pub chi: u32,
    pub rho: u32,
    pub delta: u32,
    pub sigma: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct BitomegaNode {
    pub state: i32,
    pub dir: i32,
    pub coherence: f32,
    pub entropy: f32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct BitomegaCtx {
    pub coherence_in: f32,
    pub entropy_in: f32,
    pub noise_in: f32,
    pub load: f32,
    pub seed: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedProcessState {
    pub cpu_pressure: u32,
    pub storage_pressure: u32,
    pub io_pressure: u32,
    pub matrix_determinant: i64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedRouteState {
    pub route_id: u32,
    pub route_tag: u64,
    pub toroidal: RmR_ToroidalAddr7D,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedVerifyState {
    pub computed_crc32c: u32,
    pub verify_ok: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct RmR_UnifiedArenaSlot {
    pub offset: u32,
    pub size: u32,
    pub generation: u32,
    pub in_use: u8,
}

pub const RMR_UK_MAX_SLOTS: usize = 1024;

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RmR_UnifiedKernel {
    pub initialized: u32,
    pub seed: u32,
    pub crc32c: u32,
    pub entropy: u32,
    pub stage_counter: u32,
    pub last_route_tag: u64,
    pub bitomega_node: BitomegaNode,
    pub bitomega_ctx: BitomegaCtx,
    pub bitomega_operational_state: u32,
    pub bitomega_invariant_ok: u32,
    pub bitomega_fallback_safe: u32,
    pub caps: RmR_UnifiedCapabilities,
    pub arena_base: *mut u8,
    pub arena_capacity: u32,
    pub slots: [RmR_UnifiedArenaSlot; RMR_UK_MAX_SLOTS],
}

unsafe extern "C" {
    pub fn RmR_UnifiedKernel_Init(
        kernel: *mut RmR_UnifiedKernel,
        config: *const RmR_UnifiedConfig,
    ) -> i32;
    pub fn RmR_UnifiedKernel_Shutdown(kernel: *mut RmR_UnifiedKernel) -> i32;
    pub fn RmR_UnifiedKernel_Process(
        kernel: *mut RmR_UnifiedKernel,
        cpu_cycles: u64,
        storage_read_bytes: u64,
        storage_write_bytes: u64,
        input_bytes: u64,
        output_bytes: u64,
        m00: i64,
        m01: i64,
        m10: i64,
        m11: i64,
        out: *mut RmR_UnifiedProcessState,
    ) -> i32;
    pub fn RmR_UnifiedKernel_Route(
        kernel: *mut RmR_UnifiedKernel,
        process: *const RmR_UnifiedProcessState,
        out: *mut RmR_UnifiedRouteState,
    ) -> i32;
    pub fn RmR_UnifiedKernel_Verify(
        kernel: *mut RmR_UnifiedKernel,
        data: *const u8,
        len: usize,
        expected_crc32c: u32,
        out: *mut RmR_UnifiedVerifyState,
    ) -> i32;
}

pub struct UnifiedKernelHandle {
    inner: RmR_UnifiedKernel,
}

impl UnifiedKernelHandle {
    pub fn new(seed: u32, arena_bytes: u32) -> Option<Self> {
        let mut kernel = MaybeUninit::<RmR_UnifiedKernel>::uninit();
        let cfg = RmR_UnifiedConfig { seed, arena_bytes };
        // SAFETY: `kernel` is valid writable memory and `cfg` points to a valid config.
        let rc = unsafe { RmR_UnifiedKernel_Init(kernel.as_mut_ptr(), &cfg) };
        if rc != RMR_UK_OK {
            return None;
        }
        // SAFETY: kernel was initialized by C call above.
        Some(Self {
            inner: unsafe { kernel.assume_init() },
        })
    }

    pub fn verify_crc32c(&mut self, data: &[u8]) -> Option<u32> {
        let mut out = RmR_UnifiedVerifyState::default();
        // SAFETY: kernel pointer is valid for mutable access, buffer pointer/len match `data`, and `out` is writable.
        let rc = unsafe {
            RmR_UnifiedKernel_Verify(&mut self.inner, data.as_ptr(), data.len(), 0, &mut out)
        };
        if rc == RMR_UK_OK {
            Some(out.computed_crc32c)
        } else {
            None
        }
    }

    pub fn route_for_metrics(
        &mut self,
        cpu_cycles: u64,
        storage_read_bytes: u64,
        storage_write_bytes: u64,
        input_bytes: u64,
        output_bytes: u64,
        matrix: [i64; 4],
    ) -> Option<u16> {
        let mut process = RmR_UnifiedProcessState::default();
        // SAFETY: arguments are POD and pointers are valid mutable references.
        let process_rc = unsafe {
            RmR_UnifiedKernel_Process(
                &mut self.inner,
                cpu_cycles,
                storage_read_bytes,
                storage_write_bytes,
                input_bytes,
                output_bytes,
                matrix[0],
                matrix[1],
                matrix[2],
                matrix[3],
                &mut process,
            )
        };
        if process_rc != RMR_UK_OK {
            return None;
        }

        let mut route = RmR_UnifiedRouteState::default();
        // SAFETY: kernel/process pointers are valid and route out pointer is writable.
        let route_rc = unsafe { RmR_UnifiedKernel_Route(&mut self.inner, &process, &mut route) };
        if route_rc != RMR_UK_OK {
            return None;
        }
        u16::try_from(route.route_id).ok()
    }
}

impl Drop for UnifiedKernelHandle {
    fn drop(&mut self) {
        // SAFETY: kernel was initialized in constructor; shutdown is idempotent for a live handle.
        let _ = unsafe { RmR_UnifiedKernel_Shutdown(&mut self.inner) };
    }
}
