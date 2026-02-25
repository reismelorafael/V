package com.vectras.vm.benchmark;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.core.ExecutionPolicyCenter;
import com.vectras.vm.core.ExecutionGovernance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * BenchmarkActivity - UI for running and viewing benchmark tests.
 * 
 * Provides a professional engineering interface for:
 * - Running the 79-metric benchmark suite
 * - Viewing formal engineering metrics with SI units (MB/s, MFLOPS, ns, etc.)
 * - Displaying device specifications (CPU, RAM, architecture)
 * - Exporting and sharing results
 */
public class BenchmarkActivity extends AppCompatActivity {
    private static final String TAG = "BenchmarkActivity";
    private static final String REPORT_HEADER = "VECTRAS VM PROFESSIONAL BENCHMARK REPORT";
    private static final String REPORT_HEADER_DIVIDER =
        "═══════════════════════════════════════════════════════════";
    private static final String ENVIRONMENT_HEADER = "ENVIRONMENT SNAPSHOT";
    private static final String SECTION_DIVIDER =
        "─────────────────────────────────────────────────────────";
    private static final String DIAGNOSTICS_HEADER = "BENCHMARK DIAGNOSTICS";
    private static final String GOVERNANCE_HEADER = "EXECUTION GOVERNANCE / TELEMETRY";
    private static final String SHARE_HEADER = "VECTRAS VM BENCHMARK RESULTS";
    private static final String SHARE_HEADER_DIVIDER = "════════════════════════════";
    
    // UI Elements
    private TextView tvTotalScore;
    private TextView tvScoreStatus;
    private TextView tvCpuSingleScore;
    private TextView tvCpuMultiScore;
    private TextView tvMemoryScore;
    private TextView tvStorageScore;
    private TextView tvIntegrityScore;
    private TextView tvEmulationScore;
    private LinearLayout layoutCategoryScores;
    private LinearLayout layoutProgress;
    private TextView tvProgressText;
    private LinearLayout btnRunBenchmark;
    private LinearLayout btnTuningProfile;
    private LinearLayout btnViewDetails;
    private LinearLayout btnExportResults;
    private LinearLayout btnShareResults;
    private TextView tvTuningMode;
    private TextView tvTuningSummary;
    private TextView tvReliabilityLevel;
    private TextView tvReliabilityDescription;
    private View cardFusion;
    private TextView tvFusionScore;
    private TextView tvFusionSummary;
    private TextView tvFusionMicrograph;
    private LinearProgressIndicator pbCpuSingle;
    private LinearProgressIndicator pbCpuMulti;
    private LinearProgressIndicator pbMemory;
    private LinearProgressIndicator pbStorage;
    private LinearProgressIndicator pbIntegrity;
    private LinearProgressIndicator pbEmulation;
    
    // Data
    private VectraBenchmark.BenchmarkResult[] lastResults;
    private final ExecutorService executor =
        ExecutionPolicyCenter.executor(ExecutionPolicyCenter.Channel.BENCHMARK);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_benchmark);
        
        setupToolbar();
        initViews();
        setupListeners();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar supportBar = getSupportActionBar();
        if (supportBar != null) {
            supportBar.setDisplayHomeAsUpEnabled(true);
            supportBar.setDisplayShowHomeEnabled(true);
        }
        toolbar.setTitle(getString(R.string.benchmark));
    }
    
    private void initViews() {
        tvTotalScore = findViewById(R.id.tvTotalScore);
        tvScoreStatus = findViewById(R.id.tvScoreStatus);
        tvCpuSingleScore = findViewById(R.id.tvCpuSingleScore);
        tvCpuMultiScore = findViewById(R.id.tvCpuMultiScore);
        tvMemoryScore = findViewById(R.id.tvMemoryScore);
        tvStorageScore = findViewById(R.id.tvStorageScore);
        tvIntegrityScore = findViewById(R.id.tvIntegrityScore);
        tvEmulationScore = findViewById(R.id.tvEmulationScore);
        layoutCategoryScores = findViewById(R.id.layoutCategoryScores);
        layoutProgress = findViewById(R.id.layoutProgress);
        tvProgressText = findViewById(R.id.tvProgressText);
        btnRunBenchmark = findViewById(R.id.btnRunBenchmark);
        btnTuningProfile = findViewById(R.id.btnTuningProfile);
        btnViewDetails = findViewById(R.id.btnViewDetails);
        btnExportResults = findViewById(R.id.btnExportResults);
        btnShareResults = findViewById(R.id.btnShareResults);
        tvTuningMode = findViewById(R.id.tvTuningMode);
        tvTuningSummary = findViewById(R.id.tvTuningSummary);
        tvReliabilityLevel = findViewById(R.id.tvReliabilityLevel);
        tvReliabilityDescription = findViewById(R.id.tvReliabilityDescription);
        cardFusion = findViewById(R.id.cardFusion);
        tvFusionScore = findViewById(R.id.tvFusionScore);
        tvFusionSummary = findViewById(R.id.tvFusionSummary);
        tvFusionMicrograph = findViewById(R.id.tvFusionMicrograph);
        pbCpuSingle = findViewById(R.id.pbCpuSingle);
        pbCpuMulti = findViewById(R.id.pbCpuMulti);
        pbMemory = findViewById(R.id.pbMemory);
        pbStorage = findViewById(R.id.pbStorage);
        pbIntegrity = findViewById(R.id.pbIntegrity);
        pbEmulation = findViewById(R.id.pbEmulation);
        updateTuningProfileViews();
    }
    
    private void setupListeners() {
        btnRunBenchmark.setOnClickListener(v -> runBenchmark());
        btnTuningProfile.setOnClickListener(v -> showTuningProfileDialog());
        btnViewDetails.setOnClickListener(v -> showDetailedResults());
        btnExportResults.setOnClickListener(v -> exportResults());
        btnShareResults.setOnClickListener(v -> shareResults());
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private BenchmarkManager.BenchmarkResult lastBenchmarkResult;
    private BenchmarkManager.ExecutionProfile selectedProfile = BenchmarkManager.ExecutionProfile.AUTO_ADAPTIVE;
    private volatile boolean benchmarkRunning = false;
    
    private void runBenchmark() {
        if (benchmarkRunning) {
            Toast.makeText(this, R.string.benchmark_already_running, Toast.LENGTH_SHORT).show();
            return;
        }
        benchmarkRunning = true;

        // Show progress
        layoutProgress.setVisibility(View.VISIBLE);
        btnRunBenchmark.setEnabled(false);
        btnTuningProfile.setEnabled(false);
        btnViewDetails.setVisibility(View.GONE);
        btnExportResults.setVisibility(View.GONE);
        btnShareResults.setVisibility(View.GONE);
        layoutCategoryScores.setVisibility(View.GONE);
        tvScoreStatus.setText(getString(R.string.running_benchmark));
        tvProgressText.setText(getString(R.string.preparing_benchmark));
        
        // Run benchmark in background using professional BenchmarkManager
        executor.execute(() -> {
            try {
                BenchmarkManager manager = new BenchmarkManager(this);

                // Run with progress callbacks
                BenchmarkManager.BenchmarkResult result = manager.runBenchmark(
                    new BenchmarkManager.ProgressCallback() {
                        @Override
                        public void onProgress(int metricIndex, int totalMetrics, String currentMetric) {
                            mainHandler.post(() -> {
                                tvProgressText.setText(currentMetric);
                                if (totalMetrics > 0) {
                                    int percent = Math.max(0, Math.min(100,
                                        (metricIndex * 100) / totalMetrics));
                                    tvScoreStatus.setText(getString(R.string.benchmark_progress_percent, percent));
                                }
                            });
                        }
                        
                        @Override
                        public void onWarning(String warning) {
                            mainHandler.post(() -> {
                                // Show warnings in a non-intrusive way
                                Toast.makeText(BenchmarkActivity.this, 
                                    "⚠ " + warning, Toast.LENGTH_SHORT).show();
                            });
                        }
                        
                        @Override
                        public void onComplete(BenchmarkManager.BenchmarkResult benchResult) {
                            mainHandler.post(() -> {
                                BenchmarkManager.BenchmarkResult governedResult =
                                    benchResult.withGovernanceTelemetry(null);
                                lastBenchmarkResult = governedResult;
                                lastResults = governedResult.metrics;
                                
                                // Get device specifications
                                VectraBenchmark.DeviceSpecification deviceSpec = 
                                    VectraBenchmark.getDeviceSpecification();
                                
                                // Update display
                                updateScoreDisplay(governedResult.metrics, deviceSpec);
                                layoutProgress.setVisibility(View.GONE);
                                btnRunBenchmark.setEnabled(true);
                                btnTuningProfile.setEnabled(true);
                                benchmarkRunning = false;
                                
                                // Show validation status
                                String status = benchResult.isValid ? 
                                    getString(R.string.benchmark_complete) + " ✓" : 
                                    getString(R.string.benchmark_complete) + " ⚠";
                                tvScoreStatus.setText(buildStatusWithGovernance(status, null));
                                updateReliabilityViews(governedResult.validation);
                                
                                // Show result buttons
                                btnViewDetails.setVisibility(View.VISIBLE);
                                btnExportResults.setVisibility(View.VISIBLE);
                                btnShareResults.setVisibility(View.VISIBLE);
                                layoutCategoryScores.setVisibility(View.VISIBLE);
                                cardFusion.setVisibility(View.VISIBLE);
                                
                                // Show validation dialog if there are warnings
                                if (!governedResult.validation.warnings.isEmpty() || 
                                    !governedResult.validation.errors.isEmpty()) {
                                    showValidationDialog(governedResult.validation);
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            mainHandler.post(() -> {
                                layoutProgress.setVisibility(View.GONE);
                                btnRunBenchmark.setEnabled(true);
                                btnTuningProfile.setEnabled(true);
                                benchmarkRunning = false;
                                tvScoreStatus.setText(getString(R.string.benchmark_failed));
                                // Null-safe error message
                                String errorMsg = (error != null && !error.isEmpty()) ? 
                                    error : "Unknown error occurred";
                                Toast.makeText(BenchmarkActivity.this, 
                                    "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                            });
                        }
                    }, selectedProfile, ExecutionPolicyCenter.Channel.BENCHMARK);
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    btnRunBenchmark.setEnabled(true);
                    btnTuningProfile.setEnabled(true);
                    benchmarkRunning = false;
                    tvScoreStatus.setText(getString(R.string.benchmark_failed));
                    // Null-safe error message
                    String errorMsg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? 
                        e.getMessage() : "Unknown error: " + e.getClass().getSimpleName();
                    Toast.makeText(this, "Benchmark failed: " + errorMsg, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void showTuningProfileDialog() {
        if (benchmarkRunning) {
            Toast.makeText(this, R.string.benchmark_tuning_locked_running, Toast.LENGTH_SHORT).show();
            return;
        }
        final BenchmarkManager.ExecutionProfile[] modes = new BenchmarkManager.ExecutionProfile[] {
            BenchmarkManager.ExecutionProfile.AUTO_ADAPTIVE,
            BenchmarkManager.ExecutionProfile.DETERMINISTIC,
            BenchmarkManager.ExecutionProfile.THROUGHPUT,
            BenchmarkManager.ExecutionProfile.LOW_LATENCY
        };

        String[] labels = new String[modes.length];
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            BenchmarkManager.TuningProfile profile = BenchmarkManager.buildUiPreviewProfile(modes[i]);
            labels[i] = profile.label + " (" + profile.copyStripeBytes + "B stripe)";
            if (modes[i] == selectedProfile) checked = i;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.benchmark_tuning_select)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                selectedProfile = modes[which];
                updateTuningProfileViews();
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void updateTuningProfileViews() {
        BenchmarkManager.TuningProfile profile = BenchmarkManager.buildUiPreviewProfile(selectedProfile);
        tvTuningMode.setText(profile.label);
        String summary = getString(R.string.benchmark_tuning_summary_template,
            profile.copyStripeBytes,
            profile.threadPriority,
            profile.warmupDelayMs);
        tvTuningSummary.setText(summary);
    }

    private void updateReliabilityViews(BenchmarkManager.ValidationReport validation) {
        if (validation == null) {
            tvReliabilityLevel.setText(R.string.benchmark_reliability_level_pending);
            tvReliabilityDescription.setText(R.string.benchmark_reliability_description_pending);
            return;
        }

        int confidence = (int) Math.round(validation.confidenceScore * 100);
        int level;
        if (confidence >= 90 && validation.interferenceCount <= 1) {
            level = 5;
        } else if (confidence >= 80 && validation.interferenceCount <= 2) {
            level = 4;
        } else if (confidence >= 65 && validation.interferenceCount <= 4) {
            level = 3;
        } else if (confidence >= 45) {
            level = 2;
        } else {
            level = 1;
        }

        int levelLabel;
        switch (level) {
            case 5:
                levelLabel = R.string.benchmark_reliability_level_5;
                break;
            case 4:
                levelLabel = R.string.benchmark_reliability_level_4;
                break;
            case 3:
                levelLabel = R.string.benchmark_reliability_level_3;
                break;
            case 2:
                levelLabel = R.string.benchmark_reliability_level_2;
                break;
            default:
                levelLabel = R.string.benchmark_reliability_level_1;
                break;
        }

        tvReliabilityLevel.setText(levelLabel);
        tvReliabilityDescription.setText(getString(
            R.string.benchmark_reliability_template,
            confidence,
            validation.interferenceCount,
            formatOneDecimal(validation.resultVariance)
        ));
    }

    private void updateScoreDisplay(VectraBenchmark.BenchmarkResult[] results, 
                                     VectraBenchmark.DeviceSpecification deviceSpec) {
        // Display device info instead of arbitrary score
        String deviceInfo = deviceSpec.cpuCores + " cores @ " + deviceSpec.getFormattedCpuFreq();
        tvTotalScore.setText(String.valueOf(results.length));
        tvTotalScore.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);
        
        // Calculate category summaries with real metrics
        String cpuSingleSummary = getCategorySummary(results, "CPU Single-threaded");
        String cpuMultiSummary = getCategorySummary(results, "CPU Multi-threaded");
        String memorySummary = getCategorySummary(results, "Memory");
        String storageSummary = getCategorySummary(results, "Storage");
        String integritySummary = getCategorySummary(results, "Integrity");
        String emulationSummary = getCategorySummary(results, "Emulation");
        
        tvCpuSingleScore.setText(cpuSingleSummary);
        tvCpuMultiScore.setText(cpuMultiSummary);
        tvMemoryScore.setText(memorySummary);
        tvStorageScore.setText(storageSummary);
        tvIntegrityScore.setText(integritySummary);
        tvEmulationScore.setText(emulationSummary);
        updateFusionDisplay(results);
    }
    
    /**
     * Get a representative metric value for a category.
     */
    private String getCategorySummary(VectraBenchmark.BenchmarkResult[] results, String category) {
        // Find the first result in this category and use its formatted value
        for (VectraBenchmark.BenchmarkResult r : results) {
            if (r != null && category.equals(r.category())) {
                return r.formattedValue();
            }
        }
        return "N/A";
    }
    

    private void updateFusionDisplay(VectraBenchmark.BenchmarkResult[] results) {
        int cpuSingle = categoryCompletion(results, "CPU Single-threaded");
        int cpuMulti = categoryCompletion(results, "CPU Multi-threaded");
        int memory = categoryCompletion(results, "Memory");
        int storage = categoryCompletion(results, "Storage");
        int integrity = categoryCompletion(results, "Integrity");
        int emulation = categoryCompletion(results, "Emulation");

        pbCpuSingle.setProgressCompat(cpuSingle, true);
        pbCpuMulti.setProgressCompat(cpuMulti, true);
        pbMemory.setProgressCompat(memory, true);
        pbStorage.setProgressCompat(storage, true);
        pbIntegrity.setProgressCompat(integrity, true);
        pbEmulation.setProgressCompat(emulation, true);

        int completeness = Math.round((cpuSingle + cpuMulti + memory + storage + integrity + emulation) / 6f);
        int confidence = 70;
        if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
            confidence = (int) Math.round(lastBenchmarkResult.validation.confidenceScore * 100);
        }
        int fusion = Math.round((completeness * 0.65f) + (confidence * 0.35f));
        tvFusionScore.setText(getString(R.string.benchmark_fusion_score_template, fusion));
        tvFusionSummary.setText(getString(R.string.benchmark_fusion_summary_template,
                completeness, confidence, cpuSingle, cpuMulti, memory, storage, integrity, emulation));
        String graph = buildMicrograph(cpuSingle, cpuMulti, memory, storage, integrity, emulation);
        tvFusionMicrograph.setText(graph);
    }

    private int categoryCompletion(VectraBenchmark.BenchmarkResult[] results, String category) {
        int total = 0;
        int valid = 0;
        for (VectraBenchmark.BenchmarkResult r : results) {
            if (r == null || r.category() == null) continue;
            if (!category.equals(r.category())) continue;
            total++;
            if (r.rawValue() > 0) valid++;
        }
        if (total == 0) return 0;
        return Math.round((valid * 100f) / total);
    }

    private String buildMicrograph(int... values) {
        final char[] bars = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
        if (values == null || values.length == 0) {
            return "";
        }

        int max = 1;
        for (int value : values) {
            max = Math.max(max, value);
        }

        StringBuilder graph = new StringBuilder(values.length);
        for (int value : values) {
            int index = (int) Math.round((value * 1.0 / max) * (bars.length - 1));
            index = Math.max(0, Math.min(bars.length - 1, index));
            graph.append(bars[index]);
        }
        return graph.toString();
    }

    private void showValidationDialog(BenchmarkManager.ValidationReport validation) {
        String validationReport = BenchmarkManager.formatValidationReport(validation);
        
        TextView messageView = new TextView(this);
        messageView.setText(validationReport);
        messageView.setTextIsSelectable(true);
        messageView.setTypeface(android.graphics.Typeface.MONOSPACE);
        messageView.setTextSize(9f);
        messageView.setPadding(16, 16, 16, 16);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(messageView);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Validation Report")
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
    
    private void showDetailedResults() {
        if (lastResults == null) {
            Toast.makeText(this, "No results to display", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Build comprehensive report with validation if available
        StringBuilder fullReport = new StringBuilder();
        
        // Add validation report if available
        if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
            fullReport.append(BenchmarkManager.formatValidationReport(lastBenchmarkResult.validation));
            fullReport.append("\n\n");
        }
        
        appendGovernanceSection(fullReport, lastBenchmarkResult);

        // Add benchmark results
        fullReport.append(VectraBenchmark.formatReport(lastResults));
        
        // Create a scrollable text view for the detailed results
        TextView messageView = new TextView(this);
        messageView.setText(fullReport.toString());
        messageView.setTextIsSelectable(true);
        messageView.setTypeface(android.graphics.Typeface.MONOSPACE);
        messageView.setTextSize(9f);
        messageView.setPadding(16, 16, 16, 16);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(messageView);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.detailed_results))
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
    
    private void exportResults() {
        if (lastResults == null) {
            Toast.makeText(this, "No results to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "vectras_benchmark_" + timestamp + ".txt";
                File exportDir = new File(AppConfig.maindirpath);
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                File exportFile = new File(exportDir, fileName);
                
                // Build comprehensive report with validation
                StringBuilder fullReport = new StringBuilder();
                
                // Add header
                fullReport.append(REPORT_HEADER).append("\n");
                fullReport.append("Generated: ").append(timestamp).append("\n");
                fullReport.append(REPORT_HEADER_DIVIDER).append("\n\n");
                
                // Add validation report if available
                if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
                    fullReport.append(BenchmarkManager.formatValidationReport(lastBenchmarkResult.validation));
                    fullReport.append("\n\n");
                }

                // Add environment snapshot
                if (lastBenchmarkResult != null && lastBenchmarkResult.environment != null) {
                    BenchmarkManager.EnvironmentSnapshot env = lastBenchmarkResult.environment;
                    fullReport.append(ENVIRONMENT_HEADER).append("\n");
                    fullReport.append(SECTION_DIVIDER).append("\n");
                    fullReport.append(String.format("CPU Temperature: %.1f°C\n", env.cpuTempC));
                    fullReport.append(String.format("Free Memory: %d MB\n", env.freeMemoryMb));
                    fullReport.append(String.format("Running Processes: %d\n", env.runningProcesses));
                    fullReport.append(String.format("CPU Governor: %s\n", safeValue(env.cpuGovernor)));
                    fullReport.append(String.format("CPU Info Model: %s\n", safeValue(env.cpuInfoModel)));
                    fullReport.append(String.format("CPU Info Hardware: %s\n", safeValue(env.cpuInfoHardware)));
                    fullReport.append(String.format("Primary ABI: %s\n", safeValue(env.cpuAbi)));
                    fullReport.append(String.format("Build Fingerprint: %s\n", safeValue(env.buildFingerprint)));
                    fullReport.append(String.format("Build Hardware: %s\n", safeValue(env.buildHardware)));
                    fullReport.append(String.format("Build Product: %s\n", safeValue(env.buildProduct)));
                    fullReport.append(String.format(Locale.US,
                        "Timer Drift: %.1f%%\n", env.timeSourceDriftPercent));
                    fullReport.append(String.format(Locale.US,
                        "Timer Jitter: %.1f%%\n", env.timerJitterPercent));
                    fullReport.append(String.format("Benchmark Duration: %d ms\n", lastBenchmarkResult.durationMs));
                    fullReport.append("\n\n");
                }

                if (lastBenchmarkResult != null
                    && lastBenchmarkResult.getDiagnosticsView() != null
                    && lastBenchmarkResult.getDiagnosticsView().size() > 0) {
                    fullReport.append(DIAGNOSTICS_HEADER).append("\n");
                    fullReport.append(SECTION_DIVIDER).append("\n");
                    BenchmarkManager.DiagnosticMetricsView diagnostics =
                        lastBenchmarkResult.getDiagnosticsView();
                    for (int i = 0; i < diagnostics.size(); i++) {
                        String unit = diagnostics.getUnit(i);
                        String unitLabel = unit == null || unit.isEmpty()
                            ? ""
                            : " " + unit;
                        fullReport.append(diagnostics.getName(i)).append(": ")
                            .append(diagnostics.getFormattedValue(i))
                            .append(unitLabel)
                            .append("\n");
                        String description = diagnostics.getDescription(i);
                        if (description != null && !description.isEmpty()) {
                            fullReport.append("  • ").append(description).append("\n");
                        }
                    }
                    fullReport.append("\n\n");
                }

                appendGovernanceSection(fullReport, lastBenchmarkResult);

                // Add detailed benchmark results
                fullReport.append(VectraBenchmark.formatDetailedReport(lastResults));

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(exportFile), StandardCharsets.UTF_8))) {
                    writer.write(fullReport.toString());
                }
                
                mainHandler.post(() -> {
                    Toast.makeText(this, 
                            getString(R.string.results_exported, exportFile.getAbsolutePath()), 
                            Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.export_failed) + ": " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void shareResults() {
        if (lastResults == null) {
            Toast.makeText(this, "No results to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Build comprehensive report
        StringBuilder shareText = new StringBuilder();
        
        // Add summary
        shareText.append(SHARE_HEADER).append("\n");
        shareText.append(SHARE_HEADER_DIVIDER).append("\n\n");
        
        // Add validation summary if available
        if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
            BenchmarkManager.ValidationReport val = lastBenchmarkResult.validation;
            int confidencePercent = (int) Math.round(val.confidenceScore * 100);
            shareText.append("Confidence Score: ").append(confidencePercent).append("%\n");
            shareText.append("Result Variance: ")
                .append(formatOneDecimal(val.resultVariance))
                .append("%\n");
            if (!val.warnings.isEmpty()) {
                shareText.append("Warnings: ").append(val.warnings.size()).append("\n");
            }
            shareText.append("\n");
        }

        if (lastBenchmarkResult != null
            && lastBenchmarkResult.getDiagnosticsView() != null
            && lastBenchmarkResult.getDiagnosticsView().size() > 0) {
            shareText.append("Diagnostics:\n");
            BenchmarkManager.DiagnosticMetricsView diagnostics =
                lastBenchmarkResult.getDiagnosticsView();
            for (int i = 0; i < diagnostics.size(); i++) {
                String unit = diagnostics.getUnit(i);
                String unitLabel = unit == null || unit.isEmpty()
                    ? ""
                    : " " + unit;
                shareText.append("  - ")
                    .append(diagnostics.getName(i))
                    .append(": ")
                    .append(diagnostics.getFormattedValue(i))
                    .append(unitLabel)
                    .append("\n");
            }
            shareText.append("\n");
        }
        
        appendGovernanceSection(shareText, lastBenchmarkResult);

        // Add device info
        VectraBenchmark.DeviceSpecification spec = VectraBenchmark.getDeviceSpecification();
        shareText.append("Device: ").append(spec.cpuModel).append("\n");
        shareText.append("Cores: ").append(spec.cpuCores).append("\n");
        shareText.append("RAM: ").append(spec.getFormattedRam()).append("\n\n");
        
        // Add summary metrics
        shareText.append("Metrics Completed: ").append(lastResults.length).append("/79\n\n");
        
        // Add full report
        shareText.append(VectraBenchmark.formatReport(lastResults));
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Vectras VM Professional Benchmark Results");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_results)));
    }
    
    private String buildStatusWithGovernance(String baseStatus,
                                           ExecutionGovernance.PolicyTelemetry telemetry) {
        if (telemetry == null) {
            return baseStatus;
        }
        return baseStatus + " • " + telemetry.compactEvidence();
    }

    private void appendGovernanceSection(StringBuilder output,
                                         ExecutionGovernance.PolicyTelemetry telemetry,
                                         boolean includeDescription) {
        if (telemetry == null) {
            return;
        }
        output.append(GOVERNANCE_HEADER).append("\n");
        output.append(SECTION_DIVIDER).append("\n");
        output.append("Profile: ").append(safeValue(telemetry.profileLabel)).append("\n");
        output.append("Effective SMP: ").append(telemetry.effectiveSmp).append("\n");
        output.append("Thread Limit: ").append(telemetry.maxThreads).append("\n");
        output.append("Process Limit: ").append(telemetry.maxProcesses).append("\n");
        output.append("Queue Depth: ")
            .append(telemetry.maxObservedQueueDepth)
            .append("/")
            .append(telemetry.maxQueueDepth)
            .append("\n");
        output.append("Rejection Policy: ").append(safeValue(telemetry.rejectionPolicy)).append("\n");
        output.append("Rejections: ").append(telemetry.rejectionCount).append("\n");
        output.append("CallerRuns Activations: ").append(telemetry.callerRunsCount).append("\n");
        output.append("Tasks Submitted/Completed: ")
            .append(telemetry.submittedTasks)
            .append("/")
            .append(telemetry.completedTasks)
            .append("\n");
        output.append("Aggregate Task Runtime: ").append(telemetry.totalTaskRuntimeNs).append(" ns\n");
        if (includeDescription) {
            output.append("Evidence: runtime counters captured from the governed executor during live execution.\n");
        }
        output.append("\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private static void appendGovernanceSection(StringBuilder sb, BenchmarkManager.BenchmarkResult result) {
        if (sb == null || result == null || result.governance == null) {
            return;
        }
        BenchmarkManager.ExecutionGovernance gov = result.governance;
        sb.append("EXECUTION GOVERNANCE / POLICY TELEMETRY\n");
        sb.append(SECTION_DIVIDER).append("\n");
        sb.append("Policy Profile: ").append(safeValue(gov.profile)).append("\n");
        sb.append("Effective SMP: ").append(gov.effectiveSmp).append("\n");
        sb.append("Thread Limits (core/max): ").append(gov.coreThreads).append('/').append(gov.maxThreads).append("\n");
        sb.append("Process Limit (pid_max): ").append(gov.processLimit).append("\n");
        sb.append("Observed Running Processes: ").append(gov.runningProcessesObserved).append("\n");
        sb.append("Queue Capacity: ").append(gov.queueCapacity).append("\n");
        sb.append("Max Queue Depth Observed: ").append(gov.maxObservedQueueDepth).append("\n");
        sb.append("Rejected Tasks: ").append(gov.rejectedCount).append("\n");
        sb.append("CallerRuns Enabled: ").append(gov.callerRunsEnabled ? "yes" : "no").append("\n");
        sb.append("CallerRuns Activations: ").append(gov.callerRunsCount).append("\n\n");
    }


    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private static String formatOneDecimal(double value) {
        long rounded = Math.round(value * 10);
        long absoluteRounded = Math.abs(rounded);
        long whole = absoluteRounded / 10;
        long fraction = absoluteRounded % 10;
        StringBuilder builder = new StringBuilder();
        if (rounded < 0) {
            builder.append('-');
        }
        builder.append(whole).append('.').append(fraction);
        return builder.toString();
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
