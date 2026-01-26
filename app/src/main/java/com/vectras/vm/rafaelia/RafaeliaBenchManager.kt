package com.vectras.vm.rafaelia

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vectras.vm.R
import com.vectras.vm.VMManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

object RafaeliaBenchManager {
    private const val TAG = "RafaeliaBench"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var benchRunnable: Runnable? = null

    @JvmStatic
    fun scheduleBenchIfNeeded(context: Context, vmName: String) {
        val config = RafaeliaConfig.fromPreferences(context)
        if (!config.enabled || config.mode != RafaeliaMode.BENCH) return

        val durationMs = RafaeliaSettings.benchDurationMs(context)
        if (durationMs <= 0) return

        benchRunnable?.let { handler.removeCallbacks(it) }
        benchRunnable = Runnable {
            VMManager.killallqemuprocesses(context)
            generateReportAsync(context, vmName, durationMs)
        }
        handler.postDelayed(benchRunnable!!, durationMs)
    }

    private fun generateReportAsync(context: Context, vmName: String, durationMs: Long) {
        executor.execute {
            val report = parseBenchReport(RafaeliaSettings.logFile(context), durationMs)
            if (report != null) {
                RafaeliaReportStorage.saveBenchReport(context, report)
                RafaeliaEventRecorder.recordBench(context, report, vmName)
            }
            if (context is Activity) {
                context.runOnUiThread {
                    if (context.isFinishing || context.isDestroyed) return@runOnUiThread
                    if (report == null) {
                        Toast.makeText(context, R.string.rafaelia_bench_report_missing, Toast.LENGTH_LONG).show()
                    } else {
                        showBenchDialog(context, report)
                    }
                }
            }
        }
    }

    private fun showBenchDialog(activity: Activity, report: RafaeliaBenchReport) {
        val json = report.toJson().toString(2)
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity, R.style.CenteredDialogTheme)
            .setTitle(activity.getString(R.string.rafaelia_bench_report_title))
            .setMessage(json)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
    }

    private fun parseBenchReport(logFile: File, durationMs: Long): RafaeliaBenchReport? {
        if (!logFile.exists()) return null

        val ticksPattern = Pattern.compile("(?i)ticks?\\s*[:=]\\s*(\\d+)")
        val entropyPattern = Pattern.compile("(?i)entropy(?:_avg)?\\s*[:=]\\s*([0-9.]+)")
        val coherencePattern = Pattern.compile("(?i)coherence(?:_avg)?\\s*[:=]\\s*([0-9.]+)")

        var ticks = 0L
        var entropySum = 0.0
        var entropyCount = 0
        var coherenceSum = 0.0
        var coherenceCount = 0

        BufferedReader(FileReader(logFile)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("RAFAELIA", ignoreCase = true)) {
                    val ticksMatcher = ticksPattern.matcher(line)
                    if (ticksMatcher.find()) {
                        ticks = maxOf(ticks, ticksMatcher.group(1)?.toLongOrNull() ?: 0L)
                    }
                    val entropyMatcher = entropyPattern.matcher(line)
                    if (entropyMatcher.find()) {
                        entropySum += entropyMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                        entropyCount += 1
                    }
                    val coherenceMatcher = coherencePattern.matcher(line)
                    if (coherenceMatcher.find()) {
                        coherenceSum += coherenceMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                        coherenceCount += 1
                    }
                }
                line = reader.readLine()
            }
        }

        val entropyAvg = if (entropyCount > 0) entropySum / entropyCount else 0.0
        val coherenceAvg = if (coherenceCount > 0) coherenceSum / coherenceCount else 0.0

        Log.i(TAG, String.format(Locale.US, "bench parsed ticks=%d entropy=%.6f coherence=%.6f", ticks, entropyAvg, coherenceAvg))
        return RafaeliaBenchReport(ticks, entropyAvg, coherenceAvg, durationMs)
    }
}
