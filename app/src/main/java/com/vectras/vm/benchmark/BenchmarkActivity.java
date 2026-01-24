package com.vectras.vm.benchmark;

import android.content.Intent;
import android.os.Build;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.utils.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private LinearLayout btnViewDetails;
    private LinearLayout btnExportResults;
    private LinearLayout btnShareResults;
    
    // Data
    private VectraBenchmark.BenchmarkResult[] lastResults;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
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
        btnViewDetails = findViewById(R.id.btnViewDetails);
        btnExportResults = findViewById(R.id.btnExportResults);
        btnShareResults = findViewById(R.id.btnShareResults);
    }
    
    private void setupListeners() {
        btnRunBenchmark.setOnClickListener(v -> runBenchmark());
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
    
    private void runBenchmark() {
        // Show progress
        layoutProgress.setVisibility(View.VISIBLE);
        btnRunBenchmark.setEnabled(false);
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
                                    int percent = (metricIndex * 100) / totalMetrics;
                                    tvScoreStatus.setText(String.format(java.util.Locale.US, 
                                        "Running: %d%%", percent));
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
                                lastBenchmarkResult = benchResult;
                                lastResults = benchResult.metrics;
                                
                                // Get device specifications
                                VectraBenchmark.DeviceSpecification deviceSpec = 
                                    VectraBenchmark.getDeviceSpecification();
                                
                                // Update display
                                updateScoreDisplay(benchResult.metrics, deviceSpec);
                                layoutProgress.setVisibility(View.GONE);
                                btnRunBenchmark.setEnabled(true);
                                
                                // Show validation status
                                String status = benchResult.isValid ? 
                                    getString(R.string.benchmark_complete) + " ✓" : 
                                    getString(R.string.benchmark_complete) + " ⚠";
                                tvScoreStatus.setText(status);
                                
                                // Show result buttons
                                btnViewDetails.setVisibility(View.VISIBLE);
                                btnExportResults.setVisibility(View.VISIBLE);
                                btnShareResults.setVisibility(View.VISIBLE);
                                layoutCategoryScores.setVisibility(View.VISIBLE);
                                
                                // Show validation dialog if there are warnings
                                if (!benchResult.validation.warnings.isEmpty() || 
                                    !benchResult.validation.errors.isEmpty()) {
                                    showValidationDialog(benchResult.validation);
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            mainHandler.post(() -> {
                                layoutProgress.setVisibility(View.GONE);
                                btnRunBenchmark.setEnabled(true);
                                tvScoreStatus.setText(getString(R.string.benchmark_failed));
                                // Null-safe error message
                                String errorMsg = (error != null && !error.isEmpty()) ? 
                                    error : "Unknown error occurred";
                                Toast.makeText(BenchmarkActivity.this, 
                                    "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    btnRunBenchmark.setEnabled(true);
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
                fullReport.append("VECTRAS VM PROFESSIONAL BENCHMARK REPORT\n");
                fullReport.append("Generated: ").append(timestamp).append("\n");
                fullReport.append("═══════════════════════════════════════════════════════════\n\n");
                
                // Add validation report if available
                if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
                    fullReport.append(BenchmarkManager.formatValidationReport(lastBenchmarkResult.validation));
                    fullReport.append("\n\n");
                    
                    // Add environment snapshot
                    if (lastBenchmarkResult.environment != null) {
                        BenchmarkManager.EnvironmentSnapshot env = lastBenchmarkResult.environment;
                        fullReport.append("ENVIRONMENT SNAPSHOT\n");
                        fullReport.append("─────────────────────────────────────────────────────────\n");
                        fullReport.append(String.format("CPU Temperature: %.1f°C\n", env.cpuTempC));
                        fullReport.append(String.format("Free Memory: %d MB\n", env.freeMemoryMb));
                        fullReport.append(String.format("Running Processes: %d\n", env.runningProcesses));
                        fullReport.append(String.format("CPU Governor: %s\n", env.cpuGovernor));
                        fullReport.append(String.format("CPU Info Model: %s\n", env.cpuInfoModel));
                        fullReport.append(String.format("CPU Info Hardware: %s\n", env.cpuInfoHardware));
                        fullReport.append(String.format("Primary ABI: %s\n", env.cpuAbi));
                        fullReport.append(String.format("Build Fingerprint: %s\n", env.buildFingerprint));
                        fullReport.append(String.format("Build Hardware: %s\n", env.buildHardware));
                        fullReport.append(String.format("Build Product: %s\n", env.buildProduct));
                        fullReport.append(String.format(java.util.Locale.US,
                            "Timer Drift: %.1f%%\n", env.timeSourceDriftPercent));
                        fullReport.append(String.format(java.util.Locale.US,
                            "Timer Jitter: %.1f%%\n", env.timerJitterPercent));
                        fullReport.append(String.format("Benchmark Duration: %d ms\n", lastBenchmarkResult.durationMs));
                        fullReport.append("\n\n");
                    }
                }
                
                // Add detailed benchmark results
                fullReport.append(VectraBenchmark.formatDetailedReport(lastResults));
                
                // Write to file
                FileUtils.writeToFile(exportDir.getAbsolutePath(), fileName, fullReport.toString());
                
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
        shareText.append("VECTRAS VM BENCHMARK RESULTS\n");
        shareText.append("════════════════════════════\n\n");
        
        // Add validation summary if available
        if (lastBenchmarkResult != null && lastBenchmarkResult.validation != null) {
            BenchmarkManager.ValidationReport val = lastBenchmarkResult.validation;
            shareText.append(String.format("Confidence Score: %.0f%%\n", val.confidenceScore * 100));
            shareText.append(String.format("Result Variance: %.1f%%\n", val.resultVariance));
            if (!val.warnings.isEmpty()) {
                shareText.append(String.format("Warnings: %d\n", val.warnings.size()));
            }
            shareText.append("\n");
        }
        
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
