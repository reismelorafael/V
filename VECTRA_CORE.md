# Vectra Core MVP

## Overview

Vectra Core is a minimal "information-theoretic" runtime framework for Android that implements deterministic event processing with built-in integrity verification. It's designed to treat all data (including noise) as information and provide append-only logging for forensic analysis.

## Key Concepts

### 1. Noise as Data (ρ - Rho)
**Rho (ρ)** represents information that is not yet decoded. Instead of treating noise or unexpected data as bugs to be discarded, Vectra Core preserves it as potentially valuable information. The `rho()` function updates entropy hints based on "noise" inputs.

**Definition**: `rho = syndrome + event weight`
- `syndrome`: popcount of parity differences (bit errors detected)
- `event weight`: importance factor (radio events = 10, network = 5, timer = 1)

### 2. 4-Phase Cycle Loop
Vectra Core implements a deterministic processing cycle:
1. **Input**: Poll event from priority queue
2. **Process**: Update state based on event type and payload
3. **Output**: Log state changes to append-only file
4. **Next**: Prepare for next cycle iteration

The cycle runs at 10 Hz (100ms intervals) and maintains deterministic behavior through lockstep processing.

### 2.1 Deterministic Operational Policy (Gates)
Vectra Core treats "deterministic" as a **policy of state transition**, not as a solver. The rule is fixed; the state evolves with experience:

- **Hit**: a cycle receives an event.
- **Miss**: a cycle receives no event.
- **Policy gate**: after 2 misses, reduce weighting; after 2 hits, restore weighting.

This keeps the next-state decision deterministic while still adapting to the observed stream.

### 3. Triad Physical Model (2-of-3 Consensus)
The **VectraTriad** models three physical components: CPU, RAM, and DISK. Using 2-of-3 consensus, it can detect which component is "out-of-sync":
- If CPU == RAM but different from DISK → DISK is out
- If CPU == DISK but different from RAM → RAM is out
- If RAM == DISK but different from CPU → CPU is out
- If all differ or all agree → No component is out

This provides fault detection without requiring all three to agree perfectly.

### 4. Base Cell: 4x4 Grid with Parity
Each block represents a 4×4 grid = 16 bits of data with 8 bits of parity:
- **4 row parity bits**: One per row
- **4 column parity bits**: One per column
- **Total**: 8 parity bits can detect and locate single-bit errors
- **Index mapping**: `idx = (y << 2) | x` for position (x, y) in the grid

### 5. ECC/Parity as "Borrowed Structure"
The parity bits provide redundancy to preserve data equivalence under:
- **Loss**: Missing bits can be recovered
- **Negation**: Bit flips are detected
- **Bit leak**: Gradual corruption is visible

This redundancy is "borrowed" in the sense that it doesn't add new information, but enables error detection and correction.

### 6. State Depth (1024 Flags)
**VectraState** maintains 1024 boolean flags (2^10 states) using a BitSet-like structure:
- Efficient bit operations using LongArray (16 × 64 bits)
- Branchless flag setting for performance
- Tracks various system states and event processing

### 7. "Finger" - IRQ-like Priority Events
Events are modeled as interrupt-style requests with priorities:
- **RADIO_EVENT**: Priority 10 (highest impact on entropy)
- **NETWORK_CHANGE**: Priority 5
- **USER_INPUT**: Priority 3
- **TIMER_TICK**: Priority 1
- Events are processed in priority order (higher first, then FIFO)

### 8. Append-Only BitStack Log
**VectraBitStackLog** provides deterministic evidence logging:
- Binary format: `[magic, length, meta, crc32c, payload]`
- Each record is CRC-protected for integrity
- Append-only: No modification or deletion
- Max size: 10 MB (configurable)
- Stored in app internal storage: `filesDir/vectra_core.log`

## Build Configuration

### Enabling/Disabling Vectra Core

Vectra Core is controlled by the `VECTRA_CORE_ENABLED` BuildConfig flag:

- **Debug builds**: `VECTRA_CORE_ENABLED = true` (enabled by default)
- **Release builds**: `VECTRA_CORE_ENABLED = false` (disabled by default)

### To Enable in Release:
Edit `app/build.gradle`:
```gradle
buildTypes {
    release {
        buildConfigField "boolean", "VECTRA_CORE_ENABLED", "true"
    }
}
```

### To Disable in Debug:
```gradle
buildTypes {
    debug {
        buildConfigField "boolean", "VECTRA_CORE_ENABLED", "false"
    }
}
```

## Runtime Behavior

### When Enabled
- Initializes on `Application.onCreate()`
- Starts background cycle thread at 10 Hz
- Starts timer tick thread at 1 Hz
- Creates append-only log file
- Runs self-test with 5 validation checks
- Zero impact on UI thread (all processing is background)

### When Disabled
- Single `if (!BuildConfig.VECTRA_CORE_ENABLED) return` check
- No threads started
- No files created
- No memory allocated
- Zero runtime overhead

## Self-Test Validation

On startup, Vectra Core runs 5 self-tests:
1. **Header CRC**: Verify CRC computation is correct
2. **Bit Flip Detection**: Ensure CRC detects single-bit mutations
3. **4x4 Parity**: Verify parity computation for 16-bit blocks
4. **Parity Error Detection**: Ensure parity detects bit flips
5. **Syndrome Computation**: Verify syndrome (error position) calculation

Results are logged to the append-only log with meta=0xFFFF.

## Log File Location

Logs are stored in the app's internal storage:
```
/data/data/com.vectras.vm/files/vectra_core.log
```

You can retrieve logs using ADB:
```bash
adb exec-out run-as com.vectras.vm cat files/vectra_core.log > vectra_core.log
```

Or through Android file picker if app provides access to internal storage.

## API Usage

### Post Custom Events
```kotlin
import com.vectras.vm.vectra.VectraCore
import com.vectras.vm.vectra.VectraEvent

// Post a custom event
val event = VectraEvent(
    type = VectraEvent.EventType.SYSTEM_EVENT,
    priority = 5,
    payload = "custom data".toByteArray()
)
VectraCore.postEvent(event)
```

### Check Triad State
```kotlin
val triad = VectraCore.getTriad()
val outComponent = triad.whoOut() // Returns CPU, RAM, DISK, or NONE
```

### Process Data Through Stages
```kotlin
// PSI stage: deterministic ingest
val crc = VectraCore.psi("data".toByteArray())

// RHO stage: noise as information
val entropy = VectraCore.rho("noise".toByteArray(), eventWeight = 5)

// OMEGA stage: finalize digest
val digest = VectraCore.omegaFinalize()
```

## Architecture Components

| Component | Purpose |
|-----------|---------|
| **VectraState** | 1024-bit flag array + stage counters |
| **VectraBlock** | 4×4 block with parity8 + CRC32C |
| **VectraMemPool** | Fixed-size buffer pool (no GC churn) |
| **VectraEvent** | IRQ-like priority event with payload |
| **VectraEventBus** | Thread-safe priority queue |
| **VectraCycle** | 4-phase loop processor (10 Hz) |
| **VectraTriad** | CPU/RAM/DISK 2-of-3 consensus |
| **VectraBitStackLog** | Append-only binary logger |
| **CRC32C** | Castagnoli CRC for integrity |
| **Parity** | 2D parity for 4×4 blocks |

## Performance Characteristics

- **Memory**: ~256 KB baseline (state + mempool)
- **CPU**: <1% on background thread (10 Hz cycle)
- **Storage**: Up to 10 MB log file
- **Threads**: 2 daemon threads (cycle + timer)
- **GC Impact**: Minimal (uses mempool for frequent allocations)

## Future Enhancements (Not in MVP)

- Network event source integration
- Log compression and rotation
- Error correction (not just detection)
- Multi-device state synchronization
- Configurable cycle frequency
- Export/import log format

## Signature/Version

- **Core Version**: 1.0.0-MVP
- **Log Format Version**: 1
- **Magic**: 0x56454354 ("VECT")
- **Block Magic**: 0x5645435452413031 ("VECTRA01")
