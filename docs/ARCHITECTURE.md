# Product Architecture (RAFAELIA Engine-First)

## Objective
Transform the mixed repository into a productized ecosystem where **Engine (RMR/Bitraf)** is the primary IP and **Runtime (Vectras Android)** is a showcase consumer.

## Layered topology
1. **engine/rmr/**: deterministic low-level C core (cycles, hw detect, bench, isorf, bitraf).
2. **bench/**: executable benchmark harness + statistical runner (median/p95).
3. **demo_cli/**: minimal CLI for sanity and integration.
4. **docs/**: architecture, benchmarks, whitepaper, IP map.
5. **runtime/showcase/**: bridge to Android app modules.
6. **archive/experimental/**: historical/non-critical assets isolated from core path.

## Build graph
- `librmr.a` is the engine artifact.
- `rmr_bench` links against `librmr.a` and emits CSV/JSON.
- `rafaelia_demo` and `bitraf_core` validate runtime behavior and low-level path.

## Product split
- **Produto 1: Engine** → licensing-ready C toolkit, deterministic benchmarks, integration APIs.
- **Produto 2: Runtime** → Android VM showcase for user-facing validation and distribution.

## Principles
- Engine-first prioritization (IP/protection/revenue).
- No destructive deletes: non-core moved to archive.
- Reproducibility over ad-hoc experiments.
- Minimal dependency surface on core.

---

# Architecture Document

## Documento de Arquitetura

---

<div align="center">

**Vectras VM - System Architecture**

*Technical Architecture Documentation*

*Version 1.0.0 | January 2026*

</div>

---

## Table of Contents / Índice

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [Architectural Layers](#3-architectural-layers)
4. [Component Descriptions](#4-component-descriptions)
5. [Data Flow](#5-data-flow)
6. [Design Patterns](#6-design-patterns)
7. [Security Architecture](#7-security-architecture)
8. [Performance Considerations](#8-performance-considerations)
9. [Deployment Architecture](#9-deployment-architecture)
10. [Future Architecture](#10-future-architecture)

---

## 1. Introduction

### 1.1 Purpose

This document provides a comprehensive technical description of the Vectras VM system architecture. It is intended for software developers, system architects, security auditors, and researchers who require detailed understanding of the system's internal structure and design decisions.

### 1.2 Scope

The architectural description encompasses:

- Android application components
- QEMU emulation subsystem
- Vectra Core experimental framework
- Supporting infrastructure (Firebase, networking, storage)
- Build and deployment processes

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|------------|
| QEMU | Quick Emulator - dynamic binary translation emulator |
| VM | Virtual Machine |
| ISA | Instruction Set Architecture |
| SoC | System on Chip |
| KVM | Kernel-based Virtual Machine |
| TCG | Tiny Code Generator (QEMU's JIT compiler) |
| ECC | Error-Correcting Code |
| CRC | Cyclic Redundancy Check |

---

## 2. System Overview

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     VECTRAS VM APPLICATION                      │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  PRESENTATION LAYER                      │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐ │   │
│  │  │ Main    │ │ VM      │ │ Data    │ │ Settings/       │ │   │
│  │  │Activity │ │ Manager │ │Explorer │ │ Configuration   │ │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   BUSINESS LOGIC LAYER                   │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐ │   │
│  │  │ VM      │ │ QEMU    │ │ Storage │ │ Network         │ │   │
│  │  │ Config  │ │ Params  │ │ Manager │ │ Manager         │ │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   CORE SERVICES LAYER                    │   │
│  │  ┌─────────────────┐ ┌─────────────────────────────────┐ │   │
│  │  │   MainService   │ │        Vectra Core (MVP)        │ │   │
│  │  │   (Foreground)  │ │  ┌───────┐ ┌───────┐ ┌───────┐  │ │   │
│  │  └─────────────────┘ │  │State  │ │Cycle  │ │Logger │  │ │   │
│  │                      │  └───────┘ └───────┘ └───────┘  │ │   │
│  │                      └─────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   EMULATION LAYER                        │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │                    QEMU 9.2.x                        │ │   │
│  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐  │ │   │
│  │  │  │   TCG    │ │  Device  │ │  Block   │ │ 3Dfx   │  │ │   │
│  │  │  │  Engine  │ │ Emulation│ │  Layer   │ │ Glide  │  │ │   │
│  │  │  └──────────┘ └──────────┘ └──────────┘ └────────┘  │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   PLATFORM LAYER                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │   │
│  │  │  PRoot   │ │ Alpine   │ │Terminal  │ │  Shell     │  │   │
│  │  │ (chroot) │ │ Linux    │ │ Emulator │ │  Loader    │  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                      ANDROID OS LAYER                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ ART Runtime │ Binder IPC │ Storage │ Network │ Display   │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Architectural Style

Vectras VM employs a **layered architecture** with clear separation of concerns:

1. **Presentation Layer**: Android UI components (Activities, Fragments, Adapters)
2. **Business Logic Layer**: VM configuration, parameter management, storage handling
3. **Core Services Layer**: Background services and experimental frameworks
4. **Emulation Layer**: QEMU and related emulation components
5. **Platform Layer**: PRoot, Alpine Linux userspace, terminal emulation
6. **Android OS Layer**: Host operating system services

This layered approach follows the principles outlined by Garlan and Shaw (1994) in their taxonomy of software architecture styles [1].

---

## 3. Architectural Layers

### 3.1 Presentation Layer

The presentation layer implements the Model-View-Controller (MVC) pattern adapted for Android:

```
┌─────────────────────────────────────────────────────────┐
│                    ACTIVITIES                           │
├──────────────┬──────────────┬──────────────┬───────────┤
│ SplashActivity│ MainActivity │ VMCreator   │ StartVM   │
│ (Entry point) │ (Main UI)    │ Activity    │ Activity  │
├──────────────┼──────────────┼──────────────┼───────────┤
│ DataExplorer │ AboutActivity│ WebView     │ Settings  │
│ Activity     │              │ Activity    │ Activity  │
└──────────────┴──────────────┴──────────────┴───────────┘
```

**Key Components**:

- **SplashActivity**: Application entry point, initialization sequence
- **MainActivity**: Central navigation hub, VM listing
- **VMCreatorActivity**: Virtual machine creation wizard
- **StartVM**: QEMU execution and display interface
- **DataExplorerActivity**: File system browser for VM images

### 3.2 Business Logic Layer

```kotlin
// Core business entities
class RomInfo {
    val name: String
    val path: String
    val architecture: String
    val memory: Int
    val cpuCores: Int
    val bootOptions: Map<String, String>
}

class VMManager {
    fun createVM(config: RomInfo): Boolean
    fun deleteVM(name: String): Boolean
    fun listVMs(): List<RomInfo>
    fun updateVM(config: RomInfo): Boolean
}
```

### 3.3 Core Services Layer

#### 3.3.1 MainService

A foreground service maintaining QEMU process lifecycle:

```java
public class MainService extends Service {
    private Process qemuProcess;
    private NotificationManager notificationManager;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        startQemuProcess(intent.getExtras());
        return START_STICKY;
    }
}
```

#### 3.3.2 Vectra Core Framework

The experimental Vectra Core implements an information-theoretic runtime:

```
┌─────────────────────────────────────────────────────────┐
│                    VECTRA CORE MVP                       │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │ VectraState │    │ VectraEvent │    │ VectraTriad │  │
│  │ (1024 bits) │    │  (Priority) │    │  (2-of-3)   │  │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘  │
│         │                  │                  │         │
│         ▼                  ▼                  ▼         │
│  ┌─────────────────────────────────────────────────────┐│
│  │                  VectraCycle (10 Hz)                 ││
│  │  Input → Process → Output → Next                    ││
│  └─────────────────────────────────────────────────────┘│
│         │                                               │
│         ▼                                               │
│  ┌─────────────────────────────────────────────────────┐│
│  │              VectraBitStackLog                      ││
│  │  Append-only | CRC32C protected | Binary format    ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

### 3.4 Emulation Layer

#### 3.4.1 QEMU Integration

QEMU is integrated through a shell-based invocation pattern:

```bash
# Example QEMU invocation (simplified)
qemu-system-x86_64 \
    -machine q35 \
    -cpu max \
    -m 2048 \
    -smp cores=4 \
    -drive file=disk.qcow2,format=qcow2 \
    -display sdl \
    -audiodev sdl,id=audio0 \
    -device ich9-intel-hda \
    -device hda-output,audiodev=audio0
```

#### 3.4.2 3Dfx Glide Emulation

```
┌─────────────────────────────────────────────────────────┐
│                  3DFX EMULATION PIPELINE                │
├─────────────────────────────────────────────────────────┤
│  Guest OS                                               │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Application → Glide API → Glide DLL                 ││
│  └─────────────────────────┬───────────────────────────┘│
│                            │ IPC/Shared Memory          │
│                            ▼                            │
│  QEMU                                                   │
│  ┌─────────────────────────────────────────────────────┐│
│  │ 3Dfx Device → Glide Wrapper → OpenGL ES             ││
│  └─────────────────────────┬───────────────────────────┘│
│                            │                            │
│                            ▼                            │
│  Android                                                │
│  ┌─────────────────────────────────────────────────────┐│
│  │ OpenGL ES → GPU Driver → Hardware                   ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

### 3.5 Platform Layer

#### 3.5.1 PRoot Architecture

PRoot provides userspace filesystem virtualization without root privileges:

```
┌─────────────────────────────────────────────────────────┐
│                     PROOT ABSTRACTION                   │
├─────────────────────────────────────────────────────────┤
│  Android Process                                        │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Vectras App → PRoot → QEMU                          ││
│  └─────────────────────────────────────────────────────┘│
│                            │                            │
│                            │ System call interception   │
│                            ▼                            │
│  Virtual Filesystem                                     │
│  ┌─────────────────────────────────────────────────────┐│
│  │ / (root)                                            ││
│  │ ├── bin/     → Alpine Linux binaries                ││
│  │ ├── lib/     → Shared libraries                     ││
│  │ ├── usr/     → User programs (QEMU)                 ││
│  │ └── data/    → Bind mount to Android storage        ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

---

## 4. Component Descriptions

### 4.1 Application Package Structure

```
com.vectras.vm/
├── VectrasApp.java              # Application entry point
├── SplashActivity.java          # Splash screen
├── MainService.java             # Foreground service
├── VMManager.java               # VM lifecycle management
├── VMCreatorActivity.java       # VM creation wizard
├── StartVM.java                 # QEMU execution
├── core/                        # Core utilities
├── model/                       # Data models
├── adapter/                     # RecyclerView adapters
├── utils/                       # Helper utilities
├── vectra/                      # Vectra Core framework
│   └── VectraCore.kt           # Complete Vectra implementation
├── settings/                    # Settings management
├── network/                     # Network utilities
└── x11/                         # X11/display handling
```

### 4.2 Vectra Core Components

| Component | Purpose | Implementation |
|-----------|---------|----------------|
| `VectraState` | 1024-bit flag storage | LongArray[16] |
| `VectraBlock` | 4×4 parity blocks | 16-bit data + 8-bit parity |
| `CRC32C` | Integrity verification | Castagnoli polynomial |
| `Parity` | 2D parity computation | Row + column XOR |
| `VectraMemPool` | Memory buffer management | ArrayDeque<ByteArray> |
| `VectraEvent` | Priority event model | PriorityQueue-compatible |
| `VectraEventBus` | Thread-safe event queue | ReentrantLock + PriorityQueue |
| `VectraCycle` | 4-phase processing loop | 10 Hz daemon thread |
| `VectraTriad` | 2-of-3 consensus | CPU/RAM/DISK state tracking |
| `VectraBitStackLog` | Append-only logging | RandomAccessFile |

### 4.3 External Dependencies

```gradle
dependencies {
    // Android Framework
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Kotlin
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    
    // Image Loading
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    
    // JSON Processing
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Networking
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-analytics'
    implementation 'com.google.firebase:firebase-crashlytics'
    implementation 'com.google.firebase:firebase-messaging'
}
```

---

## 5. Data Flow

### 5.1 VM Creation Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   User UI   │────▶│ VMCreator   │────▶│  VMManager  │
│  (Intent)   │     │  Activity   │     │  (validate) │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Notify    │◀────│  Storage    │◀────│  RomInfo    │
│   Success   │     │   (write)   │     │  (create)   │
└─────────────┘     └─────────────┘     └─────────────┘
```

### 5.2 VM Execution Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  StartVM    │────▶│ MainService │────▶│  PRoot      │
│  Activity   │     │ (foreground)│     │  (exec)     │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Display    │◀────│  QEMU       │◀────│  QEMU       │
│  (SDL/VNC)  │     │  (running)  │     │  (init)     │
└─────────────┘     └─────────────┘     └─────────────┘
```

### 5.3 Vectra Core Event Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Event     │────▶│  EventBus   │────▶│  VectraCycle│
│  (post)     │     │  (queue)    │     │  (poll)     │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌──────────────────────────┤
                    │     4-Phase Cycle        │
                    ▼                          │
             ┌─────────────┐            ┌──────▼──────┐
             │   Process   │───────────▶│   Output    │
             │  (update)   │            │   (log)     │
             └─────────────┘            └──────┬──────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │ BitStackLog │
                                        │  (append)   │
                                        └─────────────┘
```

---

## 6. Design Patterns

### 6.1 Patterns Employed

| Pattern | Location | Purpose |
|---------|----------|---------|
| **Singleton** | VectraCore | Global state management |
| **Object Pool** | VectraMemPool | Memory allocation optimization |
| **Observer** | VectraEventBus | Event-driven processing |
| **Strategy** | VM configuration | Pluggable boot options |
| **Facade** | VMManager | Simplified VM operations |
| **Builder** | RomInfo | Complex object construction |
| **Template Method** | VectraCycle | 4-phase processing template |

### 6.2 Singleton Pattern (VectraCore)

```kotlin
object VectraCore {
    private val initialized = AtomicBoolean(false)
    
    @JvmStatic
    fun init(context: Context) {
        if (!BuildConfig.VECTRA_CORE_ENABLED) return
        if (initialized.getAndSet(true)) return
        // Initialization logic
    }
}
```

### 6.3 Object Pool Pattern (VectraMemPool)

```kotlin
class VectraMemPool(private val chunkSize: Int, poolSize: Int) {
    private val pool = ArrayDeque<ByteArray>()
    
    fun borrow(size: Int = chunkSize): ByteArray {
        return if (size <= chunkSize && pool.isNotEmpty()) {
            pool.removeFirst()
        } else {
            ByteArray(size)
        }
    }
    
    fun release(buffer: ByteArray) {
        if (buffer.size == chunkSize && pool.size < MAX_POOL_SIZE) {
            pool.addLast(buffer)
        }
    }
}
```

---

## 7. Security Architecture

### 7.1 Security Model

```
┌─────────────────────────────────────────────────────────┐
│                  SECURITY LAYERS                        │
├─────────────────────────────────────────────────────────┤
│  Application Sandbox (Android)                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │ • Per-app UID isolation                             ││
│  │ • SELinux mandatory access control                  ││
│  │ • Storage scoped to app directory                   ││
│  └─────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────┤
│  Runtime Isolation (PRoot)                              │
│  ┌─────────────────────────────────────────────────────┐│
│  │ • Userspace filesystem virtualization               ││
│  │ • No root privileges required                       ││
│  │ • System call filtering                             ││
│  └─────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────┤
│  Data Integrity (Vectra Core)                           │
│  ┌─────────────────────────────────────────────────────┐│
│  │ • CRC32C checksums                                  ││
│  │ • Append-only logging                               ││
│  │ • Parity-based error detection                      ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

### 7.2 Threat Model

| Threat | Mitigation |
|--------|------------|
| Unauthorized VM access | Android app sandbox |
| Data corruption | CRC32C + parity verification |
| Process injection | PRoot isolation |
| Network attacks | Android firewall + app permissions |
| Malicious VM images | User-provided content disclaimer |

### 7.3 Permission Model

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 8. Performance Considerations

### 8.1 Performance Metrics

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| App startup | < 3s | Cold start to main activity |
| VM boot | < 30s | QEMU init to OS desktop |
| Emulation overhead | < 10x native | Benchmark comparison |
| Memory footprint | < 500 MB | Android Profiler |
| Battery impact | < 15%/hour | Battery stats |

### 8.2 Optimization Strategies

#### 8.2.1 Memory Optimization

- **Object Pooling**: VectraMemPool reduces GC pressure
- **Lazy Initialization**: Components loaded on demand
- **Buffer Reuse**: Pre-allocated buffers for I/O operations

#### 8.2.2 CPU Optimization

- **Background Processing**: Vectra Core runs on daemon threads
- **Batch Operations**: Events processed in cycle batches
- **Branchless Operations**: VectraState uses branchless bit operations

#### 8.2.3 I/O Optimization

- **Append-Only Logging**: Sequential writes for better throughput
- **Memory-Mapped Files**: Potential future optimization for VM disk access
- **Buffered I/O**: RandomAccessFile with system buffering

### 8.3 Benchmarking Results (Reference)

| Configuration | Device | Boot Time | Responsiveness |
|---------------|--------|-----------|----------------|
| Windows 98 | SD855 | 45s | Good |
| Linux (Tiny Core) | SD855 | 25s | Excellent |
| Windows XP | SD865 | 120s | Fair |
| Android x86 | SD888 | 60s | Good |

---

## 9. Deployment Architecture

### 9.1 Build Variants

```gradle
buildTypes {
    debug {
        buildConfigField "boolean", "VECTRA_CORE_ENABLED", "true"
        debuggable true
    }
    release {
        buildConfigField "boolean", "VECTRA_CORE_ENABLED", "false"
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android.txt')
    }
}
```

### 9.2 Distribution Channels

```
┌─────────────────────────────────────────────────────────┐
│                  DISTRIBUTION MATRIX                    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  GitHub Releases                                        │
│  ├── Stable APK (arm64-v8a, armeabi-v7a, x86_64, x86)  │
│  ├── Beta APK (per-commit builds)                       │
│  └── Bootstrap archives (per-architecture)              │
│                                                         │
│  OpenAPK                                                │
│  └── Stable APK (verification pending)                  │
│                                                         │
│  Official Website                                       │
│  └── Redirects to GitHub Releases                       │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 9.3 CI/CD Pipeline

```yaml
# Simplified workflow
name: Build and Release
on:
  push:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - name: Build APK
        run: ./gradlew assembleRelease
      - name: Upload artifacts
        uses: actions/upload-artifact@v4
```

---

## 10. Future Architecture

### 10.1 Planned Enhancements

```
┌─────────────────────────────────────────────────────────┐
│              FUTURE ARCHITECTURE ROADMAP                │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Phase 1: Performance                                   │
│  ├── Hardware-accelerated display (Vulkan backend)     │
│  ├── KVM support on rooted devices                     │
│  └── Optimized TCG for ARM64 hosts                     │
│                                                         │
│  Phase 2: Features                                      │
│  ├── VM snapshots and state persistence                │
│  ├── USB passthrough (with root)                       │
│  └── Network bridge mode                               │
│                                                         │
│  Phase 3: Vectra Core Production                        │
│  ├── Full error correction (not just detection)        │
│  ├── Network event sources                             │
│  └── Multi-device state synchronization                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 10.2 Architectural Evolution

The architecture is designed to evolve through:

1. **Modular Extraction**: Core components can be extracted into separate modules
2. **API Versioning**: Stable internal APIs enable independent component evolution
3. **Feature Flags**: BuildConfig flags control feature rollout
4. **Plugin Architecture**: Future support for third-party extensions

---

## 11. Source-Validated Runtime Addendum (2026)

This addendum synchronizes architecture documentation with the currently implemented runtime paths in the Android source tree.

### 11.1 QEMU launch orchestration

- `QemuArgsBuilder` centralizes host binary selection by architecture and applies profile-driven launch flags.
- `applyVirtioStorageHints(...)` only injects virtio-scsi/iothread hints when no equivalent flags were already supplied in user extras, reducing duplicate/conflicting CLI args.
- `applyAcceleration(...)` executes `KvmProbe.probe()` and records `KVM=ON` or `KVM=OFF(<reason>)` in QEMU process naming for post-mortem traceability.

### 11.2 Deterministic KVM readiness

`KvmProbe` currently requires all checks below to enable KVM:

1. `/dev/kvm` exists;
2. `/sys/module/kvm` exists;
3. read/write permission on `/dev/kvm`;
4. host ABI compatible with `arm64` or `x86_64`.

Any failed check yields a deterministic disabled state with explicit reason string.

### 11.3 Native fast-path boundary

`NativeFastPath` defines JNI-optional acceleration boundaries for:

- `copyBytes(...)`
- `xorChecksum(...)`
- `popcount32(...)`

The design guarantees Java fallback when JNI is unavailable, preserving portability and deterministic behavior on devices without native library loading.

### 11.4 Benchmark observability path

`BenchmarkManager` integrates:

- preflight interference checks,
- environment snapshots,
- deterministic diagnostics (timer drift/jitter, emulator signals, ABI mismatch),
- adaptive tuning profile resolution with visibility in warnings/progress callbacks.

This makes benchmark runs auditable at runtime without requiring external telemetry infrastructure.

---

## References / Referências

[1] D. Garlan and M. Shaw, "An Introduction to Software Architecture," *Advances in Software Engineering and Knowledge Engineering*, vol. 1, pp. 1-39, 1994.

[2] E. Gamma, R. Helm, R. Johnson, and J. Vlissides, *Design Patterns: Elements of Reusable Object-Oriented Software*. Addison-Wesley, 1994.

[3] M. Fowler, *Patterns of Enterprise Application Architecture*. Addison-Wesley, 2002.

[4] F. Bellard, "QEMU, a Fast and Portable Dynamic Translator," in *Proceedings of the USENIX Annual Technical Conference*, 2005, pp. 41-46.

[5] Android Developers, "Application Fundamentals," [Online]. Available: https://developer.android.com/guide/components/fundamentals

---

## Document Cross-References

| Document | Relevance |
|----------|-----------|
| [PREFACE.md](PREFACE.md) | Project context and motivation |
| [ABSTRACT.md](ABSTRACT.md) | Technical summary |
| [RESUMO.md](RESUMO.md) | Portuguese technical summary |
| [VECTRA_CORE.md](../VECTRA_CORE.md) | Detailed Vectra Core specification |
| [IMPLEMENTATION_SUMMARY.md](../IMPLEMENTATION_SUMMARY.md) | Implementation details |
| [BIBLIOGRAPHY.md](BIBLIOGRAPHY.md) | Complete reference list |

---

*© 2024-2026 Vectras VM Development Team. Licensed under GNU GPL v2.0*

*Document Version: 1.0.0 | Last Updated: January 2026*
