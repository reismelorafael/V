package com.vectras.vm.rafaelia

import androidx.annotation.StringRes
import com.vectras.vm.R

enum class RafaeliaMode(val id: Int, @StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    SILENT(0, R.string.rafaelia_mode_silent, R.string.rafaelia_mode_silent_desc),
    LOG(1, R.string.rafaelia_mode_log, R.string.rafaelia_mode_log_desc),
    TRACE(2, R.string.rafaelia_mode_trace, R.string.rafaelia_mode_trace_desc),
    SYMBIOSIS(3, R.string.rafaelia_mode_symbiosis, R.string.rafaelia_mode_symbiosis_desc),
    AUDIT(4, R.string.rafaelia_mode_audit, R.string.rafaelia_mode_audit_desc),
    BENCH(5, R.string.rafaelia_mode_bench, R.string.rafaelia_mode_bench_desc);

    companion object {
        @JvmStatic
        fun fromId(id: Int): RafaeliaMode = values().firstOrNull { it.id == id } ?: SILENT

        @JvmStatic
        fun maxId(): Int = values().maxOf { it.id }
    }
}
