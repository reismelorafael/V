# Images Index / Índice de Imagens

> **Documentation Version**: 1.1.0 | **Last Updated**: 2026-02-18

This document provides a comprehensive mapping of architecture, UI, and system images to their corresponding code implementations in the Vectras VM Android application.

---

## Table of Contents

1. [Image Checklist (Status Real)](#1-image-checklist-status-real)
2. [Image Index Table](#2-image-index-table)
3. [Layer Mappings](#3-layer-mappings)
   - [UI/UX Layer](#31-uiux-layer)
   - [QEMU/Emulation Layer](#32-qemuemulation-layer)
   - [Vectra Core / Integrity Layer](#33-vectra-core--integrity-layer)
   - [Storage/Android Layer](#34-storageandroid-layer)
4. [Actionable Improvements](#4-actionable-improvements)
5. [Code Patches](#5-code-patches)

---

## 0. Política Canônica de Ativos Visuais

Formato canônico adotado neste repositório: **híbrido controlado**.

- **Obrigatório para conteúdo local**: ASCII versionado em `docs/assets/ascii/`.
- **Permitido para binário**: apenas como referência externa com URL estável e proveniência documentada.
- **Fonte única de verdade**: [`docs/assets/MANIFEST.md`](assets/MANIFEST.md).

> Regra curta: **não existe PNG local obrigatório**; o obrigatório é a rastreabilidade no manifesto (ASCII local ou binário externo com proveniência).

---

## 1. Image Checklist (Status Real)

> **✅ Maintainer Status**: Este índice segue a política canônica híbrida controlada e referencia ativos rastreáveis no manifesto único: [`docs/assets/MANIFEST.md`](assets/MANIFEST.md).

### Tracked Images (metadata complete)

| # | Filename | Source URL | Manifest Status |
|---|----------|------------|-----------------|
| 1 | `ascii/rafaelia-fractal-architecture.ascii.md` | [3c9e94f2-fc94-4782-80e8-b884bc4c6d3e](https://github.com/user-attachments/assets/3c9e94f2-fc94-4782-80e8-b884bc4c6d3e) | ✅ Complete |
| 2 | `ascii/rafaelia-system-pipeline.ascii.md` | [aafce52f-c83b-480b-b1d4-d579256c2363](https://github.com/user-attachments/assets/aafce52f-c83b-480b-b1d4-d579256c2363) | ✅ Complete |
| 3 | `ascii/rafaelia-mathematical.ascii.md` | [d69640be-83f6-413b-b3b1-2ebbfc1e7cd4](https://github.com/user-attachments/assets/d69640be-83f6-413b-b3b1-2ebbfc1e7cd4) | ✅ Complete |
| 4 | `ascii/vectra-mystical-ui-concept.ascii.md` | [aa58501e-263c-47fe-91b6-406b373cb3f6](https://github.com/user-attachments/assets/aa58501e-263c-47fe-91b6-406b373cb3f6) | ✅ Complete |
| 5 | `ascii/ziprafa-integrity-architecture.ascii.md` | [97010343-3677-4766-b070-9fece88ba754](https://github.com/user-attachments/assets/97010343-3677-4766-b070-9fece88ba754) | ✅ Complete |
| 6 | `ascii/additional-image-01.ascii.md` | [2123ec50-7240-490a-8975-bd675ca1fa92](https://github.com/user-attachments/assets/2123ec50-7240-490a-8975-bd675ca1fa92) | ✅ Complete |
| 7 | `ascii/rafaelia-core-eye-toroid.ascii.md` | Provided via chat prompt (canonicalized in-repo) | ✅ Complete |
| 8 | `ascii/rafaelia-coherence-layers.ascii.md` | Provided via chat prompt (canonicalized in-repo) | ✅ Complete |

---

## 2. Image Index Table

Based on images with complete metadata in `docs/assets/MANIFEST.md`, here is the mapping:

| Image File | Description | System Layer | Code Paths | Expected Behavior | Gaps/Bugs | Actions |
|------------|-------------|--------------|------------|-------------------|-----------|---------|
| `ascii/rafaelia-fractal-architecture.ascii.md` | RAFAELIA fractal symbiosis architecture diagram. Shows MANDALA 10x10 Hybrid V6, RAFCODE-Φ pipeline (VAZIO→VERBO→HYPERFORMAS→RETROALIMENTAÇÃO) | **Vectra Core / Architecture** | `VectraCore.kt`, `VectraCycle`, `RafaeliaMvp.java` | Deterministic event loop processing with entropy tracking | No visual feedback of cycle state in UI | Add optional debug overlay showing Vectra cycle state |
| `ascii/rafaelia-system-pipeline.ascii.md` | Data processing pipeline: Drive→Termux→Hash/Entropia/Heatmap with ÍNDICE, FAILSAFE, ZIPRAF outputs | **Storage / Integrity** | `VectraCore.kt` (CRC32C), `VectraBitStackLog`, `FileUtils.java` | Hash verification, entropy analysis, indexed storage | No file hash verification exposed to user | Add file integrity indicator in DataExplorerActivity |
| `ascii/rafaelia-mathematical.ascii.md` | Geometric/mathematical model with Traceçilda/ICAZC formulas | **QEMU / Engine** | `QemuParamsEditorActivity.java`, `Config.java` | Mathematical parameter optimization | Complex config not user-friendly | Add preset profiles for common configurations |
| `ascii/vectra-mystical-ui-concept.ascii.md` | Vertical layered visualization concept (1008 Hz, FIAT DEI/FIAT VIAT LUX) | **UI/UX Concept** | `MainActivity.java`, layouts in `res/layout/` | Visual representation of system layers | N/A - Conceptual only | Consider animated splash/about screen |
| `ascii/ziprafa-integrity-architecture.ascii.md` | ZIPRAFA integrity framework: Data Recovery, Blake3 Hashing, Ed25519 Signing, Modular Core (Core/Crypto/FEC), CLI & API | **Integrity / Logging** | `VectraCore.kt` (CRC32C parity), `VectraBitStackLog`, `RafaeliaMvp.java` | Data integrity verification, error recovery, secure identity | Blake3/Ed25519 not implemented (CRC32C used instead) | Document roadmap for advanced crypto; current CRC32C is sufficient for MVP |
| `ascii/rafaelia-core-eye-toroid.ascii.md` | Blueprint do núcleo de observabilidade com anel toroidal (ingest→verify→index→feedback) e domínios Integridade/Segurança/Governança | **Core / Observability / Governance** | `MainActivity.java`, `LoggerFragment.java`, `VectraCore.kt`, `PermissionUtils.java`, `SOURCE_TRACEABILITY_MATRIX.md` | Coleta contínua, validação, indexação e feedback operacional rastreável | Sem dashboard explícito unificado do ciclo toroidal | Consolidar painel de estado com métricas de ciclo e evidências append-only |
| `ascii/rafaelia-coherence-layers.ascii.md` | Pilha de coerência L1→L5 (UI, Runtime, Integridade, Segurança, Governança) com feedback para operação | **Cross-layer Coherence** | `MainActivity.java`, `MainVNCActivity.java`, `QmpClient.java`, `VMStatus.java`, `MANIFEST.md` | Fluxo de promoção por camadas com retorno de status ao operador | Relações de camada estavam implícitas e dispersas | Manter matriz de mapeamento por camada atualizada em documentação canônica |

---

## 3. Layer Mappings

### 3.1 UI/UX Layer

**Relevant Images**: `ascii/vectra-mystical-ui-concept.ascii.md`

**Current Implementation Files**:
```
app/src/main/java/com/vectras/vm/
├── SplashActivity.java          # Entry splash screen
├── main/MainActivity.java       # Main navigation hub
├── VMCreatorActivity.java       # VM creation wizard
├── StartVM.java                 # VM execution display
├── DataExplorerActivity.java    # File browser
├── AboutActivity.java           # About information
└── Fragment/
    ├── LoggerFragment.java      # Log viewing
    ├── CreateImageDialogFragment.java  # Disk creation
    └── ControlersOptionsFragment.java  # Controller settings
```

**UI/UX Improvements Identified**:

| Area | Current State | Suggested Improvement | Priority |
|------|--------------|----------------------|----------|
| VM Creation Wizard | Single-screen form | Multi-step wizard with progress indicator | Medium |
| Error Dialogs | Basic AlertDialog | Consistent themed dialogs with actionable messages | High |
| Long Task Progress | Indeterminate spinner | Determinate progress with ETA for disk creation/import | High |
| Storage Permissions | Runtime prompts | Clear explanation dialog for Android 13+ SAF | High |
| Action Labels | Generic text | Clear safe/danger color coding | Medium |

### 3.2 QEMU/Emulation Layer

**Relevant Images**: `ascii/rafaelia-mathematical.ascii.md`, architecture diagrams

**Current Implementation Files**:
```
app/src/main/java/com/vectras/
├── qemu/
│   ├── MainVNCActivity.java     # VNC display
│   ├── MainSettingsManager.java # QEMU settings
│   ├── Config.java              # Configuration constants
│   └── utils/
│       ├── QmpClient.java       # QMP protocol client
│       └── FileInstaller.java   # Bootstrap installer
├── vm/
│   ├── core/
│   │   ├── ShellExecutor.java   # Shell command execution
│   │   ├── TermuxX11.java       # X11 integration
│   │   └── PulseAudio.java      # Audio subsystem
│   └── QemuParamsEditorActivity.java  # QEMU params editor
```

**QEMU Integration Points**:

| Subsystem | Code Location | Purpose | Integration Opportunity |
|-----------|--------------|---------|------------------------|
| TCG Engine | External (QEMU binary) | Dynamic binary translation | Log TCG stats via QMP |
| Block Layer | `Config.java`, VM presets | Disk I/O configuration | Add I/O performance metrics |
| Device Emulation | QEMU params | Hardware emulation | Expose device status via QMP |
| Display | `MainVNCActivity.java`, `X11Activity.java` | VM display output | Add framerate/latency overlay |

### 3.3 Vectra Core / Integrity Layer

**Relevant Images**: `ascii/rafaelia-fractal-architecture.ascii.md`, `ascii/rafaelia-system-pipeline.ascii.md`, `ascii/ziprafa-integrity-architecture.ascii.md`

**Current Implementation Files**:
```
app/src/main/java/com/vectras/vm/
├── vectra/
│   └── VectraCore.kt            # Complete Vectra Core framework
├── rafaelia/
│   └── RafaeliaMvp.java         # RAFAELIA MVP implementation
├── benchmark/
│   └── VectraBenchmark.java     # 79-metric benchmark suite
└── logger/
    ├── VectrasStatus.java       # Status tracking
    ├── VMStatus.java            # VM state logging
    └── LogItem.java             # Log entry model
```

**Vectra Core Component Mapping**:

| Image Concept | Code Implementation | Status |
|---------------|---------------------|--------|
| RAFCODE-Φ Pipeline | `VectraCycle` (10 Hz 4-phase loop) | ✅ Implemented |
| Mandala Grid | `VectraBlock` (4×4 parity blocks) | ✅ Implemented |
| Hash + Entropia | `CRC32C` + `rho()` entropy tracking | ✅ Implemented |
| Failsafe | `VectraTriad` (2-of-3 consensus) | ✅ Implemented |
| ÍNDICE | `VectraBitStackLog` (append-only) | ✅ Implemented |
| Blake3/Ed25519 | Not implemented (CRC32C used) | 📋 Roadmap |
| FEC RS256 | Not implemented (parity only) | 📋 Roadmap |

### 3.4 Storage/Android Layer

**Relevant Images**: `ascii/rafaelia-system-pipeline.ascii.md` (Drive→Termux flow)

**Current Implementation Files**:
```
app/src/main/java/com/vectras/vm/
├── utils/
│   ├── FileUtils.java           # File operations
│   ├── ZipUtils.java            # Archive handling
│   ├── TarUtils.java            # Tar extraction
│   └── PermissionUtils.java     # Permission management
├── DataExplorerActivity.java    # File browser
├── RomReceiverActivity.java     # External file handling
├── settings/
│   └── ImportExportSettingsActivity.java  # Import/export
```

**Storage Considerations**:

| Android Version | Storage Model | Code Impact |
|-----------------|--------------|-------------|
| Android 6-9 | Legacy external storage | `READ/WRITE_EXTERNAL_STORAGE` |
| Android 10 | Scoped storage (opt-out) | `requestLegacyExternalStorage` |
| Android 11-12 | Scoped storage enforced | `MANAGE_EXTERNAL_STORAGE` |
| Android 13-15 | Granular media permissions | `READ_MEDIA_*` + SAF |

---

## 4. Actionable Improvements

### 4.0 Coherence Upgrade (Enterprise Full-Stack)

The indexed architecture images with complete metadata reinforce the same architectural axis already implemented in code:

- **Product/UI axis**: operational clarity and safe navigation.
- **Runtime axis**: deterministic cycle (`Input → Process → Output → Next`) with append-only evidence.
- **Platform/Governance axis**: reproducible build and source traceability.

Documentation linkage for this enterprise cohesion pass:
- `docs/navigation/ENTERPRISE_COMPANIES.md`
- `docs/SOURCE_TRACEABILITY_MATRIX.md`
- `VECTRA_CORE.md`

### 4.1 UI/UX Patches

#### 4.1.1 Add Progress Dialog for Long Operations

**File**: `app/src/main/java/com/vectras/vm/utils/DialogUtils.java`

**Rationale**: Disk creation and import operations can take minutes. Users need visual feedback.

**Suggested Addition**:
```java
/**
 * Shows a progress dialog for long-running operations.
 * Call updateProgress() to update the progress bar.
 * Call dismissProgress() when complete.
 */
public static AlertDialog showProgressDialog(Context context, String title, String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
    
    TextView titleView = view.findViewById(R.id.progress_title);
    TextView messageView = view.findViewById(R.id.progress_message);
    ProgressBar progressBar = view.findViewById(R.id.progress_bar);
    
    titleView.setText(title);
    messageView.setText(message);
    progressBar.setIndeterminate(true);
    
    builder.setView(view);
    builder.setCancelable(false);
    
    return builder.create();
}
```

#### 4.1.2 Storage Permission Explanation Dialog

**File**: `app/src/main/java/com/vectras/vm/utils/PermissionUtils.java`

**Rationale**: Android 13+ requires clear explanation of why storage access is needed.

**Suggested Addition**:
```java
/**
 * Shows explanation dialog before requesting storage permissions.
 * Tailored for Android version-specific permission requirements.
 */
public static void showStoragePermissionExplanation(Activity activity, Runnable onProceed) {
    String message;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        message = activity.getString(R.string.storage_permission_explanation_android13);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        message = activity.getString(R.string.storage_permission_explanation_android11);
    } else {
        message = activity.getString(R.string.storage_permission_explanation_legacy);
    }
    
    new AlertDialog.Builder(activity)
        .setTitle(R.string.storage_permission_title)
        .setMessage(message)
        .setPositiveButton(R.string.proceed, (d, w) -> onProceed.run())
        .setNegativeButton(R.string.cancel, null)
        .show();
}
```

### 4.2 Integrity/Logging Patches

#### 4.2.1 Add File Hash Display in DataExplorer

**File**: `app/src/main/java/com/vectras/vm/DataExplorerActivity.java`

**Rationale**: Images show hash/integrity verification as key feature. Exposing CRC32C to users builds trust.

**Suggested Addition** (to file info dialog):
```java
// In showFileInfoDialog() method
private void showFileInfoDialog(File file) {
    // ... existing code ...
    
    // Add integrity hash
    String hashInfo = "";
    if (file.isFile() && file.length() < 100 * 1024 * 1024) { // < 100MB
        try {
            long crc = calculateCRC32C(file);
            hashInfo = String.format("\nCRC32C: %08X", crc);
        } catch (IOException e) {
            hashInfo = "\nCRC32C: (error)";
        }
    }
    
    // Add to dialog message
    message.append(hashInfo);
}

private long calculateCRC32C(File file) throws IOException {
    java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
    try (FileInputStream fis = new FileInputStream(file)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            crc.update(buffer, 0, read);
        }
    }
    return crc.getValue();
}
```

### 4.3 VM Command/Preset Patches

#### 4.3.1 Add Preset Profiles

**File**: `app/src/main/java/com/vectras/vm/VMCreatorActivity.java`

**Rationale**: Mathematical model images suggest optimized configurations. Users need simple presets.

**Suggested Addition**:
```java
private static final Map<String, String[]> PRESET_PROFILES = new HashMap<String, String[]>() {{
    put("Windows 98 (Balanced)", new String[]{"256", "1", "pentium", "cirrus"});
    put("Windows XP (Performance)", new String[]{"512", "2", "pentium3", "vmware"});
    put("Linux Minimal", new String[]{"128", "1", "max", "std"});
    put("Android x86", new String[]{"1024", "2", "max", "virtio"});
}};

private void applyPreset(String presetName) {
    String[] values = PRESET_PROFILES.get(presetName);
    if (values != null) {
        memoryEditText.setText(values[0]);
        cpuCoresSpinner.setSelection(Integer.parseInt(values[1]) - 1);
        cpuModelSpinner.setSelection(findCpuModelIndex(values[2]));
        vgaSpinner.setSelection(findVgaIndex(values[3]));
    }
}
```

### 4.4 Logging/Engine Hooks

#### 4.4.1 QEMU Event Integration with Vectra Core

**File**: `app/src/main/java/com/vectras/vm/MainService.java`

**Rationale**: Architecture images show data flowing through processing stages. QEMU events should feed Vectra Core.

**Suggested Addition**:
```java
// In startQemuProcess() method
private void postQemuEventToVectra(String eventType, byte[] payload) {
    if (BuildConfig.VECTRA_CORE_ENABLED) {
        VectraEvent event = new VectraEvent(
            VectraEvent.EventType.SYSTEM_EVENT,
            eventType.equals("boot") ? 10 : 5,
            payload
        );
        VectraCore.postEvent(event);
    }
}

// Call when QEMU starts
postQemuEventToVectra("boot", vmName.getBytes());

// Call on QEMU state changes
postQemuEventToVectra("state_change", stateBytes);
```

---

## 5. Code Patches

### Summary of Required Changes

| Layer | File | Change Type | Priority | Effort |
|-------|------|-------------|----------|--------|
| UI/UX | `DialogUtils.java` | Add progress dialog | High | Low |
| UI/UX | `PermissionUtils.java` | Add permission explanation | High | Low |
| UI/UX | `strings.xml` | Add new strings | High | Low |
| Storage | `DataExplorerActivity.java` | Add CRC32C display | Medium | Low |
| VM Config | `VMCreatorActivity.java` | Add presets | Medium | Medium |
| Logging | `MainService.java` | Add Vectra event hooks | Low | Low |

### Patch Files Location

All patch implementations should follow existing code patterns in the repository. See:
- `docs/ARCHITECTURE.md` for design patterns
- `VECTRA_CORE.md` for Vectra Core API usage
- `app/build.gradle` for build configuration

---

## Appendix A: Image Categories

### Category Distribution

```
Architecture Diagrams    ████████████ 30%
UI/UX Concepts          ████████     20%
Integrity/Crypto        ██████████   25%
Mathematical Models     ████████     20%
Other/Misc             ██           5%
```

### Recommended ASCII Diagram Organization

```
docs/assets/
└── ascii/
    ├── rafaelia-fractal-architecture.ascii.md
    ├── rafaelia-system-pipeline.ascii.md
    ├── rafaelia-mathematical.ascii.md
    ├── vectra-mystical-ui-concept.ascii.md
    ├── ziprafa-integrity-architecture.ascii.md
    └── additional-image-01.ascii.md
```

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-01 | Vectras Team | Initial documentation |

---

*© 2024-2026 Vectras VM Development Team. Licensed under GNU GPL v2.0*
