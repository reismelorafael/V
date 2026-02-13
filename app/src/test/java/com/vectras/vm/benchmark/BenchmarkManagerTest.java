package com.vectras.vm.benchmark;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for BenchmarkManager with professional validation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BenchmarkManagerTest {
    
    private Context context;
    private BenchmarkManager manager;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = new BenchmarkManager(context);
    }
    
    @Test
    public void benchmarkManager_createsSuccessfully() {
        assertNotNull(manager);
    }
    
    @Test
    public void validationReport_formatsCorrectly() {
        List<String> warnings = new ArrayList<>();
        warnings.add("High CPU temperature detected");
        warnings.add("GC activity during benchmark");
        
        List<String> errors = new ArrayList<>();
        
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            warnings, errors, 0.85, true, false, false, 12.5, 2
        );
        
        assertEquals(2, report.warnings.size());
        assertEquals(0, report.errors.size());
        assertEquals(0.85, report.confidenceScore, 0.01);
        assertTrue(report.gcDetected);
        assertFalse(report.thermalDetected);
        assertEquals(12.5, report.resultVariance, 0.01);
        assertEquals(2, report.interferenceCount);
    }
    
    @Test
    public void validationReport_formatOutput_containsKeyElements() {
        List<String> warnings = new ArrayList<>();
        warnings.add("Test warning");
        
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            warnings, new ArrayList<>(), 0.9, false, false, false, 5.0, 1
        );
        
        String formatted = BenchmarkManager.formatValidationReport(report);
        
        assertNotNull(formatted);
        assertTrue(formatted.contains("VALIDATION"));
        assertTrue(formatted.contains("Confidence"));
        assertTrue(formatted.contains("90"));
        assertTrue(formatted.contains("Test warning"));
    }
    
    @Test
    public void environmentSnapshot_capturesBasicData() {
        BenchmarkManager.EnvironmentSnapshot env = new BenchmarkManager.EnvironmentSnapshot(
            System.currentTimeMillis(), 45.0, 2048, 50, false, false, false,
            "schedutil", new long[]{1800000, 1800000, 1800000, 1800000},
            "ARM Cortex", "Qualcomm", "arm64-v8a",
            "fingerprint", "hardware", "product",
            1.5, 10.0
        );
        
        assertNotNull(env);
        assertTrue(env.timestampMs > 0);
        assertEquals(45.0, env.cpuTempC, 0.01);
        assertEquals(2048, env.freeMemoryMb);
        assertEquals(50, env.runningProcesses);
        assertFalse(env.thermalThrottling);
        assertEquals("schedutil", env.cpuGovernor);
        assertEquals(4, env.cpuFrequencies.length);
    }
    
    @Test
    public void benchmarkResult_constructsWithAllData() {
        VectraBenchmark.BenchmarkResult[] metrics = new VectraBenchmark.BenchmarkResult[1];
        metrics[0] = new VectraBenchmark.BenchmarkResult(
            0, "Test", 1000000L, "1.00 ms", "ms", "CPU", "Test metric"
        );
        
        BenchmarkManager.ValidationReport validation = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), new ArrayList<>(), 0.95, false, false, false, 3.5, 0
        );
        
        BenchmarkManager.EnvironmentSnapshot env = new BenchmarkManager.EnvironmentSnapshot(
            System.currentTimeMillis(), 40.0, 3072, 45, false, false, false,
            "performance", new long[]{2000000, 2000000},
            "ARM Cortex", "Qualcomm", "arm64-v8a",
            "fingerprint", "hardware", "product",
            2.0, 12.0
        );
        
        BenchmarkManager.BenchmarkResult result = new BenchmarkManager.BenchmarkResult(
            metrics, validation, env, null, 5000, true
        );
        
        assertNotNull(result);
        assertEquals(1, result.metrics.length);
        assertNotNull(result.validation);
        assertNotNull(result.environment);
        assertEquals(5000, result.durationMs);
        assertTrue(result.isValid);
    }
    
    @Test
    public void progressCallback_receivesUpdates() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean progressReceived = new AtomicBoolean(false);
        final AtomicReference<String> lastMetric = new AtomicReference<>("");
        
        BenchmarkManager.ProgressCallback callback = new BenchmarkManager.ProgressCallback() {
            @Override
            public void onProgress(int metricIndex, int totalMetrics, String currentMetric) {
                progressReceived.set(true);
                lastMetric.set(currentMetric);
            }
            
            @Override
            public void onWarning(String warning) {
                // Not tested here
            }
            
            @Override
            public void onComplete(BenchmarkManager.BenchmarkResult result) {
                latch.countDown();
            }
            
            @Override
            public void onError(String error) {
                latch.countDown();
            }
        };
        
        // Test callback interface
        callback.onProgress(1, 79, "Testing metric");
        assertTrue(progressReceived.get());
        assertEquals("Testing metric", lastMetric.get());
    }
    
    @Test
    public void warningCallback_receivesWarnings() {
        final AtomicReference<String> lastWarning = new AtomicReference<>("");
        
        BenchmarkManager.ProgressCallback callback = new BenchmarkManager.ProgressCallback() {
            @Override
            public void onProgress(int metricIndex, int totalMetrics, String currentMetric) {
            }
            
            @Override
            public void onWarning(String warning) {
                lastWarning.set(warning);
            }
            
            @Override
            public void onComplete(BenchmarkManager.BenchmarkResult result) {
            }
            
            @Override
            public void onError(String error) {
            }
        };
        
        callback.onWarning("Test warning message");
        assertEquals("Test warning message", lastWarning.get());
    }
    
    @Test
    public void validationReport_highConfidence_showsExcellent() {
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), new ArrayList<>(), 0.95, false, false, false, 2.0, 0
        );
        
        String formatted = BenchmarkManager.formatValidationReport(report);
        assertTrue(formatted.contains("EXCELLENT") || formatted.contains("95"));
    }
    
    @Test
    public void validationReport_lowConfidence_showsPoor() {
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), new ArrayList<>(), 0.3, true, true, true, 45.0, 5
        );
        
        String formatted = BenchmarkManager.formatValidationReport(report);
        assertTrue(formatted.contains("POOR") || formatted.contains("30"));
    }
    
    @Test
    public void validationReport_withErrors_showsErrorSection() {
        List<String> errors = new ArrayList<>();
        errors.add("Critical error 1");
        errors.add("Critical error 2");
        
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), errors, 0.5, false, false, false, 20.0, 2
        );
        
        String formatted = BenchmarkManager.formatValidationReport(report);
        assertTrue(formatted.contains("Error"));
        assertTrue(formatted.contains("Critical error 1"));
    }
    
    @Test
    public void environmentSnapshot_cpuFrequencies_handlesMultipleCores() {
        long[] freqs = new long[8];
        for (int i = 0; i < 8; i++) {
            freqs[i] = 1800000L + (i * 100000L); // Varied frequencies
        }
        
        BenchmarkManager.EnvironmentSnapshot env = new BenchmarkManager.EnvironmentSnapshot(
            System.currentTimeMillis(), 50.0, 2048, 40, false, false, false,
            "interactive", freqs,
            "ARM Cortex", "Qualcomm", "arm64-v8a",
            "fingerprint", "hardware", "product",
            1.0, 8.0
        );
        
        assertEquals(8, env.cpuFrequencies.length);
        assertEquals(1800000L, env.cpuFrequencies[0]);
        assertEquals(2500000L, env.cpuFrequencies[7]);
    }
    
    @Test
    public void benchmarkResult_invalidWhenErrors() {
        VectraBenchmark.BenchmarkResult[] metrics = new VectraBenchmark.BenchmarkResult[1];
        metrics[0] = new VectraBenchmark.BenchmarkResult(
            0, "Test", 1000L, "1.00 μs", "μs", "CPU", "Test"
        );
        
        List<String> errors = new ArrayList<>();
        errors.add("Fatal error");
        
        BenchmarkManager.ValidationReport validation = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), errors, 0.4, false, false, false, 50.0, 3
        );
        
        BenchmarkManager.EnvironmentSnapshot env = new BenchmarkManager.EnvironmentSnapshot(
            System.currentTimeMillis(), 40.0, 2048, 50, false, false, false,
            "powersave", new long[]{800000, 800000},
            "ARM Cortex", "Qualcomm", "arm64-v8a",
            "fingerprint", "hardware", "product",
            2.5, 15.0
        );
        
        BenchmarkManager.BenchmarkResult result = new BenchmarkManager.BenchmarkResult(
            metrics, validation, env, null, 5000, false
        );
        
        assertFalse(result.isValid);
        assertFalse(result.validation.errors.isEmpty());
    }
    
    @Test
    public void validationReport_detectsAllInterferenceTypes() {
        BenchmarkManager.ValidationReport report = new BenchmarkManager.ValidationReport(
            new ArrayList<>(), new ArrayList<>(), 0.6, true, true, true, 30.0, 4
        );
        
        assertTrue(report.gcDetected);
        assertTrue(report.thermalDetected);
        assertTrue(report.memoryPressure);
        assertTrue(report.resultVariance > 25.0);
        assertTrue(report.interferenceCount >= 3);
    }
}
