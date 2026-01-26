package com.vectras.vm.rafaelia

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File

object RafaeliaSettings {
    const val KEY_RAFAELIA_ENABLED = "rafaeliaEnabled"
    const val KEY_RAFAELIA_MODE = "rafaeliaMode"
    const val KEY_RAFAELIA_TICK_MS = "rafaeliaTickMs"
    const val KEY_RAFAELIA_DEBUG = "rafaeliaDebug"
    const val KEY_RAFAELIA_LOG_CAPTURE = "rafaeliaLogCapture"
    const val KEY_RAFAELIA_BENCH_DURATION = "rafaeliaBenchDurationSec"
    const val KEY_RAFAELIA_BITSTACK = "rafaeliaBitStack"

    private const val DEFAULT_BENCH_SECONDS = 30

    @JvmStatic
    fun isLogCaptureEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_RAFAELIA_LOG_CAPTURE, true)
    }

    @JvmStatic
    fun isBitStackEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_RAFAELIA_BITSTACK, false)
    }

    @JvmStatic
    fun benchDurationMs(context: Context): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val seconds = prefs.getString(KEY_RAFAELIA_BENCH_DURATION, DEFAULT_BENCH_SECONDS.toString())
            ?.toLongOrNull() ?: DEFAULT_BENCH_SECONDS.toLong()
        return (if (seconds > 0) seconds else DEFAULT_BENCH_SECONDS.toLong()) * 1000L
    }

    @JvmStatic
    fun rafaeliaDir(context: Context): File {
        val dir = File(context.filesDir, "rafaelia")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @JvmStatic
    fun logFile(context: Context): File {
        return File(rafaeliaDir(context), "rafaelia.log")
    }

    @JvmStatic
    fun benchReportFile(context: Context): File {
        return File(rafaeliaDir(context), "bench_report.json")
    }

    @JvmStatic
    fun bitStackFile(context: Context): File {
        return File(rafaeliaDir(context), "rafaelia_events.bin")
    }

    @JvmStatic
    fun bitStackJsonlFile(context: Context): File {
        return File(rafaeliaDir(context), "rafaelia_events.jsonl")
    }
}
