package com.vectras.vm.rafaelia

import android.content.Context
import java.io.File
import java.io.FileWriter

object RafaeliaReportStorage {
    @JvmStatic
    fun saveBenchReport(context: Context, report: RafaeliaBenchReport): File {
        val file = RafaeliaSettings.benchReportFile(context)
        FileWriter(file, false).use { writer ->
            writer.write(report.toJson().toString(2))
        }
        return file
    }

    @JvmStatic
    fun loadBenchReport(context: Context): String? {
        val file = RafaeliaSettings.benchReportFile(context)
        if (!file.exists()) return null
        return file.readText()
    }
}
