package com.vectras.vm.localization

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class LocaleManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "vectras_locale_prefs"
        private const val KEY_CURRENT_LANGUAGE = "current_language"
        private const val KEY_DOWNLOADED_LANGUAGES = "downloaded_languages"
        private const val LANG_DIR = "lang_modules"

        @Volatile
        private var instance: LocaleManager? = null

        fun getInstance(context: Context): LocaleManager {
            return instance ?: synchronized(this) {
                instance ?: LocaleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCurrentLanguage(): String {
        return prefs.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
    }

    fun setCurrentLanguage(languageCode: String) {
        prefs.edit().putString(KEY_CURRENT_LANGUAGE, languageCode).apply()
    }

    fun getDownloadedLanguages(): Set<String> {
        val defaultSet = setOf("en")
        val downloaded = prefs.getStringSet(KEY_DOWNLOADED_LANGUAGES, defaultSet) ?: defaultSet
        return downloaded + "en"
    }

    private fun markLanguageDownloaded(languageCode: String) {
        val current = getDownloadedLanguages().toMutableSet()
        current.add(languageCode)
        prefs.edit().putStringSet(KEY_DOWNLOADED_LANGUAGES, current).apply()
    }

    fun isLanguageDownloaded(languageCode: String): Boolean {
        if (languageCode == "en") return true
        return getDownloadedLanguages().contains(languageCode)
    }

    private fun getLangDir(): File {
        val dir = File(context.filesDir, LANG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getLangFile(languageCode: String): File {
        return File(getLangDir(), "$languageCode.json")
    }

    suspend fun downloadLanguageModule(
        languageCode: String,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val module = LanguageModule.getByCode(languageCode) ?: return@withContext false

        if (module.isBuiltIn) {
            return@withContext true
        }

        var connection: HttpURLConnection? = null
        try {
            onProgress?.invoke(0)
            connection = (URL(module.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) {
                return@withContext false
            }

            onProgress?.invoke(50)

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parseJsonMap(body)
            if (parsed.isEmpty()) {
                return@withContext false
            }

            onProgress?.invoke(75)

            getLangFile(languageCode).writeText(body)
            markLanguageDownloaded(languageCode)

            onProgress?.invoke(100)
            true
        } catch (e: Exception) {
            android.util.Log.e("LocaleManager", "Failed to download language module: $languageCode", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteLanguageModule(languageCode: String): Boolean {
        if (languageCode == "en") return false

        val langFile = getLangFile(languageCode)
        if (langFile.exists()) {
            langFile.delete()
        }

        val current = getDownloadedLanguages().toMutableSet()
        current.remove(languageCode)
        prefs.edit().putStringSet(KEY_DOWNLOADED_LANGUAGES, current).apply()

        if (getCurrentLanguage() == languageCode) {
            setCurrentLanguage("en")
        }

        return true
    }

    fun getModuleStrings(languageCode: String): Map<String, String>? {
        if (languageCode == "en") return null

        val langFile = getLangFile(languageCode)
        if (!langFile.exists()) return null

        return try {
            parseJsonMap(langFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun getAllLanguageModules(): List<LanguageModule> {
        val downloaded = getDownloadedLanguages()
        return LanguageModule.getSupportedLanguages().map { module ->
            module.copy(isDownloaded = downloaded.contains(module.languageCode))
        }
    }

    fun applyLocale(context: Context): Context {
        val languageCode = getCurrentLanguage()
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    fun updateConfiguration(resources: Resources) {
        val languageCode = getCurrentLanguage()
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            config.locale = locale
        }

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun getDownloadedModulesSize(): Long {
        return getLangDir().listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clearAllModules() {
        getLangDir().listFiles()?.forEach { it.delete() }
        prefs.edit()
            .putStringSet(KEY_DOWNLOADED_LANGUAGES, setOf("en"))
            .putString(KEY_CURRENT_LANGUAGE, "en")
            .apply()
    }

    private fun parseJsonMap(content: String): Map<String, String> {
        val json = JSONObject(content)
        val out = LinkedHashMap<String, String>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            out[key] = json.optString(key, "")
        }
        return out
    }
}
