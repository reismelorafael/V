package com.vectras.vm.rafaelia

import android.content.Context
import android.util.Log
import com.vectras.vm.vectra.VectraBitStackLog
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RafaeliaEventRecorder {
    private const val TAG = "RafaeliaEvents"
    private val lock = ReentrantLock()
    private var logger: VectraBitStackLog? = null

    private fun ensureLogger(context: Context): VectraBitStackLog? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        lock.withLock {
            if (logger == null) {
                logger = VectraBitStackLog(RafaeliaSettings.bitStackFile(context))
            }
        }
        return logger
    }

    @JvmStatic
    fun recordStart(context: Context, vmName: String) {
        record(context, "start", JSONObject().put("vm", vmName))
    }

    @JvmStatic
    fun recordStop(context: Context, vmName: String) {
        record(context, "stop", JSONObject().put("vm", vmName))
        snapshot(context)
    }

    @JvmStatic
    fun recordCrash(context: Context, reason: String) {
        record(context, "crash", JSONObject().put("reason", reason))
        snapshot(context)
    }

    @JvmStatic
    fun recordRecoverable(context: Context, category: String, details: String) {
        record(context, "recoverable", JSONObject().put("category", category).put("details", details))
    }

    @JvmStatic
    fun recordBench(context: Context, report: RafaeliaBenchReport, vmName: String) {
        val payload = report.toJson().put("vm", vmName).put("event", "bench")
        record(context, "bench", payload)
        snapshot(context)
    }

    @JvmStatic
    fun snapshot(context: Context): File? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        val source = RafaeliaSettings.bitStackFile(context)
        if (!source.exists()) return null
        val snapshot = File(RafaeliaSettings.rafaeliaDir(context), "event_snapshot_${timestamp()}.bin")
        source.copyTo(snapshot, overwrite = true)
        exportJsonSnapshot(context)
        return snapshot
    }

    @JvmStatic
    fun exportJsonSnapshot(context: Context): File? {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return null
        val source = RafaeliaSettings.bitStackJsonlFile(context)
        if (!source.exists()) return null
        val snapshot = File(RafaeliaSettings.rafaeliaDir(context), "event_snapshot_${timestamp()}.json")
        source.copyTo(snapshot, overwrite = true)
        return snapshot
    }

    private fun record(context: Context, type: String, payload: JSONObject) {
        if (!RafaeliaSettings.isBitStackEnabled(context)) return
        payload.put("event", type)
        payload.put("timestamp", System.currentTimeMillis())
        val logger = ensureLogger(context)
        val bytes = payload.toString().toByteArray()
        logger?.append(bytes, type.hashCode())
        appendJsonl(context, payload)
        Log.d(TAG, "recorded event=$type")
    }

    private fun appendJsonl(context: Context, payload: JSONObject) {
        val file = RafaeliaSettings.bitStackJsonlFile(context)
        FileWriter(file, true).use { writer ->
            writer.append(payload.toString()).append("\n")
        }
    }

    private fun timestamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return formatter.format(Date())
    }
}
