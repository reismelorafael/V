package com.vectras.vm.localization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LocaleManagerTest {

    private lateinit var context: Context
    private lateinit var localeManager: LocaleManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLocaleManagerSingleton()

        context.getSharedPreferences("vectras_locale_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        File(context.filesDir, "lang_modules").deleteRecursively()

        localeManager = LocaleManager.getInstance(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "lang_modules").deleteRecursively()
        context.getSharedPreferences("vectras_locale_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        resetLocaleManagerSingleton()
    }

    @Test
    fun deleteLanguageModule_whenFileDeletionFails_returnsFalseAndKeepsState() {
        val prefs = context.getSharedPreferences("vectras_locale_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("downloaded_languages", setOf("en", "pt"))
            .putString("current_language", "pt")
            .commit()

        val langDir = File(context.filesDir, "lang_modules")
        val modulePathAsDirectory = File(langDir, "pt.json")
        val nestedFile = File(modulePathAsDirectory, "nested.txt")
        nestedFile.parentFile?.mkdirs()
        nestedFile.writeText("block-delete")

        val deleted = localeManager.deleteLanguageModule("pt")

        assertFalse(deleted)
        assertTrue(localeManager.getDownloadedLanguages().contains("pt"))
        assertTrue(localeManager.getCurrentLanguage() == "pt")
    }

    private fun resetLocaleManagerSingleton() {
        val field = LocaleManager::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }
}
