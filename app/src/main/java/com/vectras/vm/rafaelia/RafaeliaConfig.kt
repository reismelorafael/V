package com.vectras.vm.rafaelia

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * RafaeliaConfig maps UI preferences into QEMU -rafaelia hook arguments.
 */
data class RafaeliaConfig(
    val enabled: Boolean,
    val mode: RafaeliaMode,
    val tickMs: Int,
    val debug: Boolean
) {
    fun isValid(): Boolean {
        return mode.id in 0..RafaeliaMode.maxId() && tickMs > 0
    }

    fun sanitized(): RafaeliaConfig {
        val safeMode = RafaeliaMode.fromId(mode.id)
        val safeTick = if (tickMs > 0) tickMs else DEFAULT_TICK_MS
        return copy(mode = safeMode, tickMs = safeTick)
    }

    fun toQemuArgument(): String? {
        if (!enabled) return null
        val safe = sanitized()
        return "-rafaelia enable=on,mode=${safe.mode.id},tick_ms=${safe.tickMs},debug=${if (safe.debug) 1 else 0}"
    }

    companion object {
        private const val DEFAULT_TICK_MS = 10

        @JvmStatic
        fun fromPreferences(context: Context): RafaeliaConfig {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val enabled = prefs.getBoolean(RafaeliaSettings.KEY_RAFAELIA_ENABLED, false)
            val modeId = prefs.getString(RafaeliaSettings.KEY_RAFAELIA_MODE, "0")?.toIntOrNull() ?: 0
            val tickMs = prefs.getString(RafaeliaSettings.KEY_RAFAELIA_TICK_MS, DEFAULT_TICK_MS.toString())
                ?.toIntOrNull() ?: DEFAULT_TICK_MS
            val debug = prefs.getBoolean(RafaeliaSettings.KEY_RAFAELIA_DEBUG, false)
            return RafaeliaConfig(enabled, RafaeliaMode.fromId(modeId), tickMs, debug)
        }
    }
}
