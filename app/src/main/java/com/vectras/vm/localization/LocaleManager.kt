package com.vectras.vm.localization

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Manages language modules for the Vectras VM app.
 * Provides functionality for:
 * - Downloading language modules on-demand
 * - Switching between languages
 * - Storing downloaded language strings
 * - Managing language preferences
 */
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * Get the currently selected language code
     */
    fun getCurrentLanguage(): String {
        return prefs.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
    }

    /**
     * Set the current language
     */
    fun setCurrentLanguage(languageCode: String) {
        prefs.edit().putString(KEY_CURRENT_LANGUAGE, languageCode).apply()
    }

    /**
     * Get list of downloaded language codes
     */
    fun getDownloadedLanguages(): Set<String> {
        val defaultSet = setOf("en") // English is always available
        val downloaded = prefs.getStringSet(KEY_DOWNLOADED_LANGUAGES, defaultSet) ?: defaultSet
        return downloaded + "en"
    }

    /**
     * Mark a language as downloaded
     */
    private fun markLanguageDownloaded(languageCode: String) {
        val current = getDownloadedLanguages().toMutableSet()
        current.add(languageCode)
        prefs.edit().putStringSet(KEY_DOWNLOADED_LANGUAGES, current).apply()
    }

    /**
     * Check if a language module is downloaded
     */
    fun isLanguageDownloaded(languageCode: String): Boolean {
        if (languageCode == "en") return true // English is built-in
        return getDownloadedLanguages().contains(languageCode)
    }

    /**
     * Get the language module storage directory
     */
    private fun getLangDir(): File {
        val dir = File(context.filesDir, LANG_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the file for a specific language module
     */
    private fun getLangFile(languageCode: String): File {
        return File(getLangDir(), "$languageCode.json")
    }

    /**
     * Download a language module
     * @param languageCode The language code to download
     * @param onProgress Callback for download progress (0-100)
     * @return true if successful, false otherwise
     */
    suspend fun downloadLanguageModule(
        languageCode: String,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val module = LanguageModule.getByCode(languageCode) ?: return@withContext false
        
        if (module.isBuiltIn) {
            return@withContext true
        }

        try {
            onProgress?.invoke(0)
            
            val request = Request.Builder()
                .url(module.downloadUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext false
            }

            onProgress?.invoke(50)

            val body = response.body?.string() ?: return@withContext false
            
            // Validate JSON
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(body, type)
            } catch (e: Exception) {
                return@withContext false
            }

            onProgress?.invoke(75)

            // Save to file
            val langFile = getLangFile(languageCode)
            langFile.writeText(body)

            markLanguageDownloaded(languageCode)
            
            onProgress?.invoke(100)
            true
        } catch (e: IOException) {
            android.util.Log.e("LocaleManager", "Failed to download language module: $languageCode", e)
            false
        }
    }

    /**
     * Delete a downloaded language module
     */
    fun deleteLanguageModule(languageCode: String): Boolean {
        if (languageCode == "en") return false // Can't delete built-in

        val langFile = getLangFile(languageCode)
        if (langFile.exists()) {
            langFile.delete()
        }

        val current = getDownloadedLanguages().toMutableSet()
        current.remove(languageCode)
        prefs.edit().putStringSet(KEY_DOWNLOADED_LANGUAGES, current).apply()

        // If current language was deleted, switch to English
        if (getCurrentLanguage() == languageCode) {
            setCurrentLanguage("en")
        }

        return true
    }

    /**
     * Get string translations from a downloaded language module
     */
    fun getModuleStrings(languageCode: String): Map<String, String>? {
        if (languageCode == "en") return null // Use built-in resources for English

        val langFile = getLangFile(languageCode)
        if (!langFile.exists()) return null

        return try {
            val content = langFile.readText()
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(content, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a list of all language modules with their download status
     */
    fun getAllLanguageModules(): List<LanguageModule> {
        val downloaded = getDownloadedLanguages()
        return LanguageModule.getSupportedLanguages().map { module ->
            module.copy(isDownloaded = downloaded.contains(module.languageCode))
        }
    }

    /**
     * Apply the selected locale to the context
     */
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

    /**
     * Update the resources configuration with the selected locale
     */
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

    /**
     * Get the storage size used by downloaded language modules
     */
    fun getDownloadedModulesSize(): Long {
        return getLangDir().listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Clear all downloaded language modules
     */
    fun clearAllModules() {
        getLangDir().listFiles()?.forEach { it.delete() }
        prefs.edit()
            .putStringSet(KEY_DOWNLOADED_LANGUAGES, setOf("en"))
            .putString(KEY_CURRENT_LANGUAGE, "en")
            .apply()
    }
}
