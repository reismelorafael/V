package com.vectras.vm.rafaelia

import org.json.JSONObject

/**
 * Simple bench report extracted from RAFAELIA runtime logs.
 */
data class RafaeliaBenchReport(
    val ticks: Long,
    val entropyAvg: Double,
    val coherenceAvg: Double,
    val durationMs: Long
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("ticks", ticks)
            .put("entropy_avg", entropyAvg)
            .put("coherence_avg", coherenceAvg)
            .put("duration_ms", durationMs)
    }
}
