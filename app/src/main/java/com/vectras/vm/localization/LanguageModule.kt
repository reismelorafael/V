package com.vectras.vm.localization

/**
 * Represents a downloadable language module for the app.
 * Language modules are designed to be downloaded on-demand to save device resources.
 */
data class LanguageModule(
    val languageCode: String,
    val languageName: String,
    val nativeName: String,
    val version: Int,
    val sizeBytes: Long,
    val isBuiltIn: Boolean = false,
    var isDownloaded: Boolean = false,
    var downloadProgress: Int = 0
) {
    companion object {
        /**
         * Returns the list of supported language modules.
         * English is built-in and always available.
         * Other languages can be downloaded as modules.
         */
        fun getSupportedLanguages(): List<LanguageModule> = listOf(
            LanguageModule(
                languageCode = "en",
                languageName = "English",
                nativeName = "English",
                version = 1,
                sizeBytes = 0,
                isBuiltIn = true,
                isDownloaded = true
            ),
            LanguageModule(
                languageCode = "pt",
                languageName = "Portuguese",
                nativeName = "Português",
                version = 1,
                sizeBytes = 50000,
                isBuiltIn = false
            ),
            LanguageModule(
                languageCode = "es",
                languageName = "Spanish",
                nativeName = "Español",
                version = 1,
                sizeBytes = 50000,
                isBuiltIn = false
            ),
            LanguageModule(
                languageCode = "fr",
                languageName = "French",
                nativeName = "Français",
                version = 1,
                sizeBytes = 50000,
                isBuiltIn = false
            ),
            LanguageModule(
                languageCode = "de",
                languageName = "German",
                nativeName = "Deutsch",
                version = 1,
                sizeBytes = 50000,
                isBuiltIn = false
            )
        )
        
        /**
         * Get language module by code
         */
        fun getByCode(code: String): LanguageModule? {
            return getSupportedLanguages().find { it.languageCode == code }
        }
    }
}
