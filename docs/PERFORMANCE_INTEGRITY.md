# Performance Measurement and Integrity Validation Guide

This document describes how to measure performance and validate data integrity in the Vectras VM Android application.

## Table of Contents
1. [Performance Measurement](#performance-measurement)
2. [Integrity Validation](#integrity-validation)
3. [Golden Test Vectors](#golden-test-vectors)
4. [Running Tests](#running-tests)
5. [Best Practices](#best-practices)

---

## Performance Measurement

### Overview

Vectras VM includes a comprehensive performance monitoring system that measures:
- **Boot time**: Time from VM start to ready state
- **Disk throughput**: Sequential and random read/write speeds
- **Input latency**: Response time for user interactions
- **Memory usage**: Current and peak memory consumption

### Using PerformanceMonitor

```java
import com.vectras.vm.core.PerformanceMonitor;

// Get the singleton instance
PerformanceMonitor monitor = PerformanceMonitor.getInstance();

// Measure boot time
monitor.markBootStart();
// ... VM boot process ...
monitor.markBootEnd();
double bootSec = monitor.getBootTimeSec();
Log.d("Performance", "Boot time: " + bootSec + " seconds");

// Measure input latency
monitor.markInputStart();
// ... process input ...
monitor.markInputEnd();
long latencyUs = monitor.getLastInputLatencyUs();
Log.d("Performance", "Input latency: " + latencyUs + " μs");

// Run disk benchmark
File testDir = context.getCacheDir();
PerformanceMonitor.DiskBenchmarkResult result = monitor.runDiskBenchmark(testDir, 10); // 10 MB test
Log.d("Performance", result.toString());

// Generate comprehensive report
String report = monitor.generateReport();
Log.d("Performance", report);
```

### Disk Benchmark Results

The disk benchmark measures:
- **Sequential Read**: MB/s for reading consecutive data
- **Sequential Write**: MB/s for writing consecutive data
- **Random Read IOPS**: Input/Output operations per second for random 4K reads
- **Random Write IOPS**: Input/Output operations per second for random 4K writes

### Expected Performance Values

| Device Class | Seq Read | Seq Write | Random Read | Random Write |
|-------------|----------|-----------|-------------|--------------|
| High-End    | >500 MB/s | >400 MB/s | >10000 IOPS | >8000 IOPS |
| Mid-Range   | 200-500 MB/s | 150-400 MB/s | 5000-10000 IOPS | 3000-8000 IOPS |
| Entry-Level | 100-200 MB/s | 80-150 MB/s | 1000-5000 IOPS | 500-3000 IOPS |

---

## Integrity Validation

### Overview

All mathematical and bitwise operations in Vectras VM are validated using:
- **Golden test vectors**: Fixed inputs with known outputs
- **Code-derived calculations**: No OCR or image-based derivation
- **CRC32C checksums**: Data integrity verification

### MathUtils Functions

The `MathUtils` class provides stable mathematical implementations:

```java
import com.vectras.vm.core.MathUtils;

// Integer logarithm (floor)
int log = MathUtils.log2Floor(1024);  // Returns 10

// Integer square root
int sqrt = MathUtils.isqrt(100);  // Returns 10

// 64-bit mixing function
long mixed = MathUtils.mix64(12345L);

// 4x4 matrix parity (2D ECC-lite)
int parity = MathUtils.parity2D8(0x1234);

// Triad consensus (2-of-3 fault detection)
int whoOut = MathUtils.whoOutTriad(100, 100, 200);  // Returns 2 (DISK)
```

### Golden Test Vectors

Each function has defined golden test vectors for validation:

#### log2Floor
| Input | Expected Output |
|-------|-----------------|
| 1 | 0 |
| 2 | 1 |
| 4 | 2 |
| 8 | 3 |
| 1024 | 10 |
| 2147483647 | 30 |

#### isqrt (Integer Square Root)
| Input | Expected Output |
|-------|-----------------|
| 0 | 0 |
| 1 | 1 |
| 4 | 2 |
| 9 | 3 |
| 100 | 10 |
| 2147483647 | 46340 |

#### parity2D8 (4x4 Matrix Parity)
| Input (hex) | Expected Output (hex) |
|-------------|----------------------|
| 0x0000 | 0x00 |
| 0x0001 | 0x11 |
| 0xFFFF | 0x00 |
| 0xAAAA | 0x00 |

#### mix64 (SplitMix64)
| Input | Expected Output (hex) |
|-------|----------------------|
| 0 | 0x0000000000000000 |
| 1 | 0x5692161D100B05E5 |
| GOLDEN_GAMMA | 0xE220A8397B1DCDAF |

#### whoOutTriad (2-of-3 Consensus)
| CPU | RAM | DISK | Expected |
|-----|-----|------|----------|
| 100 | 100 | 200 | 2 (DISK out) |
| 100 | 200 | 100 | 1 (RAM out) |
| 200 | 100 | 100 | 0 (CPU out) |
| 100 | 100 | 100 | 3 (NONE) |
| 1 | 2 | 3 | 3 (UNKNOWN) |

---

## Running Tests

### Unit Tests

Run all unit tests:

```bash
./gradlew test
```

Run specific test class:

```bash
./gradlew test --tests "com.vectras.vm.core.MathUtilsTest"
./gradlew test --tests "com.vectras.vm.benchmark.VectraBenchmarkTest"
```

### Test Categories

1. **MathUtilsTest**: Tests for mathematical functions
   - `log2Floor_*`: Logarithm tests
   - `isqrt_*`: Square root tests
   - `parity2D8_*`: 2D parity tests
   - `whoOutTriad_*`: Consensus tests

2. **VectraBenchmarkTest**: Tests for benchmark functions
   - `parity2D8*`: Parity computation tests
   - `syndrome*`: Syndrome detection tests
   - `whoOut*`: Triad consensus tests
   - `mix64*`: Hash mixing tests

### Validating Results

Each test validates:
1. **Correctness**: Output matches golden test vector
2. **Edge cases**: Handles boundary conditions
3. **Error handling**: Throws appropriate exceptions
4. **Determinism**: Same input always produces same output

---

## Best Practices

### Anti-OCR Policy

**IMPORTANT**: All calculations in Vectras VM must be:
1. ✅ Derived from code (algorithms, formulas in source)
2. ✅ Validated with golden test vectors
3. ✅ Documented with mathematical basis
4. ❌ NOT derived from OCR (text extracted from images)
5. ❌ NOT copied from screenshots without verification

### Adding New Mathematical Functions

When adding new mathematical functions:

1. **Document the algorithm**:
   ```java
   /**
    * Computes floor(log2(n)) using bit scanning.
    * 
    * Algorithm: 31 - numberOfLeadingZeros gives position of highest set bit.
    * 
    * Golden test vectors:
    * - log2Floor(1) = 0
    * - log2Floor(1024) = 10
    */
   ```

2. **Add golden test vectors**:
   ```java
   @Test
   public void log2Floor_goldenVectors() {
       assertEquals(0, MathUtils.log2Floor(1));
       assertEquals(10, MathUtils.log2Floor(1024));
   }
   ```

3. **Test edge cases**:
   ```java
   @Test
   public void log2Floor_maxInt_returns30() {
       assertEquals(30, MathUtils.log2Floor(Integer.MAX_VALUE));
   }
   ```

4. **Validate error handling**:
   ```java
   @Test
   public void log2Floor_zeroOrNegative_throwsException() {
       assertThrows(IllegalArgumentException.class, () -> MathUtils.log2Floor(0));
   }
   ```

### Performance Measurement Guidelines

1. **Warm up**: Run benchmarks multiple times, discard first results
2. **Isolate**: Minimize background activity during measurement
3. **Repeat**: Take multiple samples and average results
4. **Document**: Record device specs and conditions

---

## CRC32C Integrity

### How It Works

Vectras VM uses CRC32C (Castagnoli) for data integrity:

```java
// Computing CRC32C
byte[] data = "Hello, World!".getBytes();
int crc = CRC32C.update(0, data);

// Verifying integrity
int storedCrc = getStoredCrc();
int computedCrc = CRC32C.update(0, data);
boolean valid = (storedCrc == computedCrc);
```

### 2D Parity (4x4 Matrix)

For enhanced error detection:

```java
// Create block with parity
int data16 = 0x1234;
int packed = VectraBlock.create4x4Block(data16);

// Verify integrity
boolean valid = VectraBlock.verify4x4Block(packed);

// Detect errors
int storedParity = VectraBlock.extractParity(packed);
int computedParity = Parity.parity2D8(data16);
int syndrome = Parity.syndrome(storedParity, computedParity);
if (syndrome > 0) {
    Log.w("Integrity", "Detected " + syndrome + " parity mismatches");
}
```

---

## References

- **SplitMix64**: https://xorshift.di.unimi.it/splitmix64.c
- **CRC32C**: https://en.wikipedia.org/wiki/Cyclic_redundancy_check
- **Newton's Method**: https://en.wikipedia.org/wiki/Integer_square_root

---

*Last updated: January 2026*
*Version: 1.0.0*
