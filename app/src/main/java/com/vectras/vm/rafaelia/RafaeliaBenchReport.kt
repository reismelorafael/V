package com.vectras.vm.rafaelia

import org.json.JSONObject

/**
 * Simple bench report extracted from RAFAELIA runtime logs.
 */
data class RafaeliaBenchReport(
    val ticks: Long,
    val entropyAvg: Double,
    val coherenceAvg: Double,
    val durationMs: Long,
    val benchProfile: String,
    val benchStrideBytes: Int,
    val benchMatrixN: Int,
    val autotuneEnabled: Boolean,
    val tcgTbSize: Int
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("ticks", ticks)
            .put("entropy_avg", entropyAvg)
            .put("coherence_avg", coherenceAvg)
            .put("duration_ms", durationMs)
            .put("bench_profile", benchProfile)
            .put("bench_stride_bytes", benchStrideBytes)
            .put("bench_matrix_n", benchMatrixN)
            .put("autotune_enabled", autotuneEnabled)
            .put("tcg_tb_size", tcgTbSize)
    }
}
