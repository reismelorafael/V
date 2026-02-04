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
    const val KEY_RAFAELIA_AUTOTUNE = "rafaeliaAutotuneEnabled"
    const val KEY_RAFAELIA_TCG_TB_SIZE = "rafaeliaTcgTbSize"
    const val KEY_RAFAELIA_BENCH_PROFILE = "rafaeliaBenchProfile"
    const val KEY_RAFAELIA_BENCH_STRIDE = "rafaeliaBenchStrideBytes"
    const val KEY_RAFAELIA_BENCH_MATRIX = "rafaeliaBenchMatrixN"

    private const val DEFAULT_BENCH_SECONDS = 30
    private const val DEFAULT_TB_SIZE = 2048
    private const val DEFAULT_BENCH_STRIDE = 2
    private const val DEFAULT_BENCH_MATRIX = 4

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
        val base = if (seconds > 0) seconds else DEFAULT_BENCH_SECONDS.toLong()
        val profile = prefs.getString(KEY_RAFAELIA_BENCH_PROFILE, "standard") ?: "standard"
        val multiplier = when (profile) {
            "light" -> 0.5
            "intensive" -> 2.0
            else -> 1.0
        }
        val scaled = (base * multiplier).toLong().coerceAtLeast(5L)
        return scaled * 1000L
    }

    @JvmStatic
    fun isAutotuneEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_RAFAELIA_AUTOTUNE, true)
    }

    @JvmStatic
    fun tcgTbSize(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(KEY_RAFAELIA_TCG_TB_SIZE, DEFAULT_TB_SIZE.toString())
            ?.toIntOrNull() ?: DEFAULT_TB_SIZE
        return value.coerceAtLeast(256)
    }

    @JvmStatic
    fun benchStrideBytes(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(KEY_RAFAELIA_BENCH_STRIDE, DEFAULT_BENCH_STRIDE.toString())
            ?.toIntOrNull() ?: DEFAULT_BENCH_STRIDE
        return value.coerceAtLeast(1)
    }

    @JvmStatic
    fun benchMatrixN(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(KEY_RAFAELIA_BENCH_MATRIX, DEFAULT_BENCH_MATRIX.toString())
            ?.toIntOrNull() ?: DEFAULT_BENCH_MATRIX
        return value.coerceIn(2, 32)
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
