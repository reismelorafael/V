package com.vectras.vm.rafaelia

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vectras.vm.R
import com.vectras.vm.databinding.ActivityRafaeliaLogsBinding
import java.io.RandomAccessFile
import java.util.Locale

class RafaeliaLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRafaeliaLogsBinding
    private val handler = Handler(Looper.getMainLooper())
    private var filePointer = 0L
    private var currentFilter = LogFilter.ALL
    private val logLines: ArrayDeque<String> = ArrayDeque()

    private val pollRunnable = object : Runnable {
        override fun run() {
            readLogIncrementally()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRafaeliaLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.logPath.text = getString(
            R.string.rafaelia_log_path_format,
            RafaeliaSettings.logFile(this).absolutePath
        )

        binding.logFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(binding.filterInit.id) -> LogFilter.INIT
                checkedIds.contains(binding.filterTick.id) -> LogFilter.TICK
                checkedIds.contains(binding.filterShutdown.id) -> LogFilter.SHUTDOWN
                checkedIds.contains(binding.filterEntropy.id) -> LogFilter.ENTROPY
                checkedIds.contains(binding.filterCoherence.id) -> LogFilter.COHERENCE
                else -> LogFilter.ALL
            }
            renderLogs()
        }

        binding.btnSnapshotBin.setOnClickListener {
            val file = RafaeliaEventRecorder.snapshot(this)
            val message = file?.absolutePath ?: getString(R.string.rafaelia_snapshot_missing)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        binding.btnSnapshotJson.setOnClickListener {
            val file = RafaeliaEventRecorder.exportJsonSnapshot(this)
            val message = file?.absolutePath ?: getString(R.string.rafaelia_snapshot_missing)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        refreshBenchReport()
    }

    override fun onResume() {
        super.onResume()
        refreshBenchReport()
        handler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pollRunnable)
    }

    private fun refreshBenchReport() {
        val report = RafaeliaReportStorage.loadBenchReport(this)
        binding.benchReportText.text = report ?: getString(R.string.rafaelia_bench_report_empty)
    }

    private fun readLogIncrementally() {
        val file = RafaeliaSettings.logFile(this)
        if (!file.exists()) {
            binding.logOutput.text = getString(R.string.rafaelia_log_empty)
            return
        }

        RandomAccessFile(file, "r").use { raf ->
            // Maintenance note: if log capture/VM restarts and truncates the file,
            // reset incremental state to avoid seeking past EOF and mixing old/new sessions.
            if (file.length() < filePointer) {
                filePointer = 0L
                logLines.clear()
            }
            raf.seek(filePointer)
            var line = raf.readLine()
            while (line != null) {
                val decoded = line.toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8)
                logLines.add(decoded)
                if (logLines.size > 300) {
                    logLines.removeFirst()
                }
                line = raf.readLine()
            }
            filePointer = raf.filePointer
        }
        renderLogs()
    }

    private fun renderLogs() {
        if (logLines.isEmpty()) {
            binding.logOutput.text = getString(R.string.rafaelia_log_empty)
            return
        }
        val filtered = logLines.filter { filterLine(it) }
        binding.logOutput.text = if (filtered.isEmpty()) {
            getString(R.string.rafaelia_log_filtered_empty)
        } else {
            filtered.joinToString("\n")
        }
    }

    private fun filterLine(line: String): Boolean {
        if (!line.contains("RAFAELIA", ignoreCase = true)) return false
        val lower = line.lowercase(Locale.US)
        return when (currentFilter) {
            LogFilter.ALL -> true
            LogFilter.INIT -> lower.contains("init")
            LogFilter.TICK -> lower.contains("tick")
            LogFilter.SHUTDOWN -> lower.contains("shutdown")
            LogFilter.ENTROPY -> lower.contains("entropy")
            LogFilter.COHERENCE -> lower.contains("coherence")
        }
    }

    enum class LogFilter {
        ALL,
        INIT,
        TICK,
        SHUTDOWN,
        ENTROPY,
        COHERENCE
    }
}
