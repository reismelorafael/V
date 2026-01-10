package com.vectras.vm.settings

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vectras.qemu.MainSettingsManager
import com.vectras.vm.R
import com.vectras.vm.localization.LanguageModule
import com.vectras.vm.localization.LanguageModuleAdapter
import com.vectras.vm.localization.LocaleManager
import com.vectras.vm.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Activity for managing language modules.
 * Allows users to download, select, and delete language packs.
 */
class LanguageModulesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: View
    private lateinit var clearAllButton: MaterialButton
    private lateinit var adapter: LanguageModuleAdapter
    private lateinit var localeManager: LocaleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_modules)

        // Initialize LocaleManager
        localeManager = LocaleManager.getInstance(this)

        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.lang_modules_title)
        }
        toolbar.setNavigationOnClickListener { onBack() }

        // Initialize views
        recyclerView = findViewById(R.id.language_modules_recycler)
        loadingProgress = findViewById(R.id.loading_progress)
        clearAllButton = findViewById(R.id.clear_all_button)

        setupRecyclerView()
        setupClearAllButton()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    private fun setupRecyclerView() {
        val currentLanguage = localeManager.getCurrentLanguage()
        val modules = localeManager.getAllLanguageModules()

        adapter = LanguageModuleAdapter(
            modules = modules,
            onDownloadClick = { module -> downloadModule(module) },
            onDeleteClick = { module -> confirmDeleteModule(module) },
            onSelectClick = { module -> selectModule(module) },
            selectedLanguage = currentLanguage
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupClearAllButton() {
        clearAllButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.lang_clear_all)
                .setMessage(R.string.lang_clear_all_confirm)
                .setPositiveButton(R.string.ok) { _, _ ->
                    localeManager.clearAllModules()
                    adapter.updateModules(localeManager.getAllLanguageModules())
                    adapter.setSelectedLanguage("en")
                    showRestartDialog()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun downloadModule(module: LanguageModule) {
        loadingProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = localeManager.downloadLanguageModule(module.languageCode) { progress ->
                runOnUiThread {
                    adapter.updateProgress(module.languageCode, progress)
                }
            }

            loadingProgress.visibility = View.GONE

            if (success) {
                Toast.makeText(
                    this@LanguageModulesActivity,
                    R.string.lang_download_success,
                    Toast.LENGTH_SHORT
                ).show()
                adapter.updateModules(localeManager.getAllLanguageModules())
            } else {
                Toast.makeText(
                    this@LanguageModulesActivity,
                    R.string.lang_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
                adapter.updateProgress(module.languageCode, 0)
            }
        }
    }

    private fun confirmDeleteModule(module: LanguageModule) {
        AlertDialog.Builder(this)
            .setTitle(module.languageName)
            .setMessage(R.string.lang_delete_confirm)
            .setPositiveButton(R.string.remove) { _, _ ->
                localeManager.deleteLanguageModule(module.languageCode)
                adapter.updateModules(localeManager.getAllLanguageModules())
                adapter.setSelectedLanguage(localeManager.getCurrentLanguage())
                Toast.makeText(this, R.string.lang_delete_success, Toast.LENGTH_SHORT).show()

                if (module.languageCode == localeManager.getCurrentLanguage()) {
                    showRestartDialog()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectModule(module: LanguageModule) {
        if (!module.isDownloaded && !module.isBuiltIn) {
            Toast.makeText(this, R.string.lang_download_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        localeManager.setCurrentLanguage(module.languageCode)
        adapter.setSelectedLanguage(module.languageCode)
        Toast.makeText(
            this,
            getString(R.string.lang_selected, module.languageName),
            Toast.LENGTH_SHORT
        ).show()
        showRestartDialog()
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.lang_restart_required)
            .setMessage(R.string.lang_restart_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                MainActivity.isNeedRecreate = true
                recreate()
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun onBack() {
        startActivity(Intent(this, MainSettingsManager::class.java))
        finish()
    }
}
