package com.vectras.vm.localization

object NetworkEndpoints {
    private const val LANGUAGE_MODULE_BASE_URL =
        "https://raw.githubusercontent.com/rafaelmeloreisnovo/Vectras-VM-Android/main/resources/lang"

    fun languageModule(languageCode: String): String {
        return "$LANGUAGE_MODULE_BASE_URL/$languageCode.json"
    }
}
