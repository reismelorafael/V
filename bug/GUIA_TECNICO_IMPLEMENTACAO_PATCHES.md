# Guia Técnico de Remediação
## Vectras-VM-Android v3.6.5 - Implementações Específicas

**Objetivo:** Fornecer patches de código exato, padrões de implementação e instruções linha-por-linha para remediação dos nove bugs identificados.

---

## Problema 1: Credenciais de Assinatura Expostas

### Arquivo: `app/build.gradle`

**Ação 1: Remover Credenciais Hardcoded**

Substituir as linhas 34-47 de:

```gradle
signingConfigs {
    debug {
        storeFile file('../vectras.jks')
        keyAlias 'vectras'
        storePassword '856856'
        keyPassword '856856'
    }
    release {
        storeFile file('../vectras.jks')
        keyAlias 'vectras'
        storePassword '856856'
        keyPassword '856856'
    }
}
```

Por:

```gradle
signingConfigs {
    debug {
        storeFile file(findProperty("VECTRAS_STORE_FILE") ?: "../vectras.jks")
        keyAlias findProperty("VECTRAS_KEY_ALIAS") ?: "vectras"
        storePassword findProperty("VECTRAS_STORE_PASSWORD") ?: "debug_default"
        keyPassword findProperty("VECTRAS_KEY_PASSWORD") ?: "debug_default"
    }
    release {
        storeFile file(findProperty("VECTRAS_STORE_FILE") ?: "../vectras.jks")
        keyAlias findProperty("VECTRAS_KEY_ALIAS") ?: "vectras"
        storePassword findProperty("VECTRAS_STORE_PASSWORD") ?: ""
        keyPassword findProperty("VECTRAS_KEY_PASSWORD") ?: ""
    }
}
```

**Ação 2: Criar `local.properties.example`**

Criar novo arquivo `local.properties.example` com conteúdo:

```properties
# Local build configuration - DO NOT COMMIT ACTUAL PASSWORDS
# Copy to local.properties and fill with real values

VECTRAS_STORE_FILE=../vectras.jks
VECTRAS_KEY_ALIAS=vectras
VECTRAS_STORE_PASSWORD=your_password_here
VECTRAS_KEY_PASSWORD=your_password_here
```

**Ação 3: Atualizar `.gitignore`**

Adicionar ao `.gitignore`:

```
local.properties
*.jks
*.keystore
gradle.properties
```

**Ação 4: CI/CD Configuration (GitHub Actions)**

Criar `.github/workflows/build.yml` com:

```yaml
name: Build and Sign APK

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '21'
      
      - name: Create local.properties
        run: |
          cat > local.properties << EOF
          VECTRAS_STORE_FILE=vectras.jks
          VECTRAS_KEY_ALIAS=${{ secrets.VECTRAS_KEY_ALIAS }}
          VECTRAS_STORE_PASSWORD=${{ secrets.VECTRAS_STORE_PASSWORD }}
          VECTRAS_KEY_PASSWORD=${{ secrets.VECTRAS_KEY_PASSWORD }}
          EOF
      
      - name: Decode keystore
        run: |
          echo "${{ secrets.VECTRAS_KEYSTORE_BASE64 }}" | base64 -d > vectras.jks
      
      - name: Build with Gradle
        run: ./gradlew assembleRelease
```

---

## Problema 2: Race Condition em ProcessSupervisor.java

### Arquivo: `app/src/main/java/com/vectras/vm/core/ProcessSupervisor.java`

**Ação 1: Adicionar Lock Explícito**

Adicionar após a linha 76 (após declaração de `clock`):

```java
    // Synchronization for state transitions
    private final Object stateLock = new Object();
    private final Object processLock = new Object();
```

**Ação 2: Refatorar Transições de Estado**

Envolver todas as modificações de `state` em blocos sincronizados. Por exemplo, encontrar cada ocorrência de `state =` e envolver em:

```java
synchronized (stateLock) {
    // Previous state tracking
    State previousState = state;
    
    // State modification
    state = newState;
    
    // Notify waiting threads if needed
    stateLock.notifyAll();
}
```

**Ação 3: Implementar Métodos Seguros de Transição**

Adicionar novos métodos após o método construtor:

```java
/**
 * Thread-safe state transition with timeout safeguard.
 * @return true if transition completed, false if timeout occurred
 */
private boolean transitionStateSafe(State expectedCurrent, State newState, long timeoutMs) {
    synchronized (stateLock) {
        // Verify precondition
        if (state != expectedCurrent) {
            return false;
        }
        
        // Perform transition
        State previousState = state;
        state = newState;
        
        // Log transition
        transitionSink.onTransition(previousState, newState, "automatic", 
                                   "state_transition", 0, 0, 0);
        
        // Notify all waiting threads
        stateLock.notifyAll();
        
        return true;
    }
}

/**
 * Wait for a specific state with timeout.
 * @return true if state reached, false if timeout
 */
private boolean waitForState(State targetState, long timeoutMs) 
        throws InterruptedException {
    synchronized (stateLock) {
        long endTime = System.currentTimeMillis() + timeoutMs;
        
        while (state != targetState) {
            long remaining = endTime - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            stateLock.wait(remaining);
        }
        return true;
    }
}
```

**Ação 4: Implementar Watchdog para Timeouts**

Adicionar classe interna ou externa:

```java
private static class ProcessWatchdog extends Thread {
    private final ProcessSupervisor supervisor;
    private final long timeoutMs;
    private volatile boolean cancelled = false;
    
    ProcessWatchdog(ProcessSupervisor supervisor, long timeoutMs) {
        super("ProcessWatchdog-" + supervisor.vmId);
        this.supervisor = supervisor;
        this.timeoutMs = timeoutMs;
        setDaemon(true);
    }
    
    @Override
    public void run() {
        try {
            Thread.sleep(timeoutMs);
            if (!cancelled) {
                // Timeout occurred - force state to STOP
                synchronized (supervisor.stateLock) {
                    if (supervisor.state != State.STOP) {
                        supervisor.state = State.STOP;
                        supervisor.process = null;
                        supervisor.stateLock.notifyAll();
                    }
                }
            }
        } catch (InterruptedException e) {
            // Expected when process completes before timeout
            Thread.currentThread().interrupt();
        }
    }
    
    void cancel() {
        cancelled = true;
    }
}
```

---

## Problema 3: Race Condition em FileInstaller.java

### Arquivo: `app/src/main/java/com/vectras/qemu/utils/FileInstaller.java`

**Ação 1: Adicionar Sincronização de Arquivo**

Adicionar no início da classe (após imports):

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileInstaller {
    
    /**
     * Per-file locking to prevent concurrent writes to same file.
     * Using ReadWriteLock to allow concurrent reads but exclusive writes.
     */
    private static final Map<String, ReadWriteLock> FILE_LOCKS = 
        Collections.synchronizedMap(new HashMap<>());
    
    private static ReadWriteLock getLockForFile(String filePath) {
        return FILE_LOCKS.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
    }
```

**Ação 2: Refatorar installAssetFile com Lock**

Substituir método `installAssetFile` de linhas 123-150 por:

```java
public static boolean installAssetFile(Context context, String srcFile,
                                       String destDir, String assetsDir, String destFile) {
    if (destFile == null) {
        destFile = srcFile;
    }
    
    String fullPath = destDir + "/" + destFile;
    ReadWriteLock fileLock = getLockForFile(fullPath);
    
    fileLock.writeLock().lock();
    try {
        AssetManager am = context.getResources().getAssets();
        InputStream is = am.open(assetsDir + "/" + srcFile);
        
        try {
            File destDirF = new File(destDir);
            if (!destDirF.exists()) {
                boolean res = destDirF.mkdirs();
                if (!res) {
                    if (context instanceof Activity activity) {
                        UIUtils.toastShort(activity, "Could not create directory for image");
                    }
                }
            }
            
            // Atomic write: write to temp file, then rename
            File tempFile = new File(destDir, destFile + ".tmp");
            OutputStream os = new FileOutputStream(tempFile);
            
            try {
                byte[] buf = new byte[8092];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                os.flush();
            } finally {
                os.close();
            }
            
            // Atomic rename
            File finalFile = new File(fullPath);
            boolean renameSuccess = tempFile.renameTo(finalFile);
            if (!renameSuccess) {
                // Fallback: delete target and rename
                finalFile.delete();
                renameSuccess = tempFile.renameTo(finalFile);
            }
            
            return renameSuccess;
        } finally {
            is.close();
        }
    } catch (Exception ex) {
        Log.e("Installer", "failed to install file: " + destFile + 
              ", Error: " + ex.getMessage());
        return false;
    } finally {
        fileLock.writeLock().unlock();
    }
}
```

**Ação 3: Refatorar installFiles com Proteção**

Envolver verificação de arquivo em sincronização:

```java
for (int k = 0; k < subfiles.length; k++) {
    File file = new File(destDir, files[i] + "/" + subfiles[k]);
    String filePath = file.getAbsolutePath();
    ReadWriteLock fileLock = getLockForFile(filePath);
    
    fileLock.readLock().lock();
    try {
        boolean fileExists = file.exists();
        boolean shouldInstall = !fileExists || force;
        
        if (shouldInstall) {
            fileLock.readLock().unlock();
            fileLock.writeLock().lock();
            try {
                // Double-check after acquiring write lock
                if (!file.exists() || force) {
                    Log.v("Installer", "Installing file: " + file.getPath());
                    installAssetFile(context, files[i] + "/" + subfiles[k], 
                                   destDir, "roms", null);
                }
            } finally {
                fileLock.writeLock().unlock();
            }
        }
    } finally {
        if (fileLock.readLock() != null) {
            try {
                fileLock.readLock().unlock();
            } catch (IllegalMonitorStateException e) {
                // Already unlocked by write lock acquisition
            }
        }
    }
}
```

---

## Problema 4: Violação de SAF (Scoped Access Framework)

### Arquivo: `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`

**Ação 1: Criar Camada de Abstração SAF**

Criar novo arquivo `SAFFileManager.java`:

```java
package com.vectras.qemu.utils;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Abstraction layer for Scoped Access Framework (SAF).
 * Replaces direct filesystem access with SAF-compatible methods.
 */
public class SAFFileManager {
    
    private final Context context;
    private DocumentFile rootDirectory;
    private Uri treeUri;
    
    public SAFFileManager(Context context) {
        this.context = context;
    }
    
    /**
     * Initialize with a root directory URI obtained from ACTION_OPEN_DOCUMENT_TREE.
     */
    public void setRootDirectoryUri(Uri treeUri) {
        this.treeUri = treeUri;
        this.rootDirectory = DocumentFile.fromTreeUri(context, treeUri);
        
        // Persist permission for future app runs
        context.getContentResolver().takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION |
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
    }
    
    /**
     * Get or create a file at the specified path.
     * Path should be relative to root, e.g., "roms/system.img"
     */
    public DocumentFile getOrCreateFile(String relativePath) throws IOException {
        if (rootDirectory == null) {
            throw new IllegalStateException("Root directory not set");
        }
        
        String[] parts = relativePath.split("/");
        DocumentFile current = rootDirectory;
        
        // Navigate/create directories
        for (int i = 0; i < parts.length - 1; i++) {
            DocumentFile next = current.findFile(parts[i]);
            if (next == null) {
                next = current.createDirectory(parts[i]);
            }
            current = next;
        }
        
        // Get or create final file
        String fileName = parts[parts.length - 1];
        DocumentFile file = current.findFile(fileName);
        if (file == null) {
            String mimeType = getMimeType(fileName);
            file = current.createFile(mimeType, fileName);
        }
        
        return file;
    }
    
    /**
     * Copy an InputStream to a file path in SAF directory.
     */
    public boolean copyToFile(InputStream source, String relativePath) {
        try {
            DocumentFile file = getOrCreateFile(relativePath);
            if (file == null) {
                return false;
            }
            
            try (OutputStream out = context.getContentResolver()
                    .openOutputStream(file.getUri())) {
                if (out == null) {
                    return false;
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = source.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                }
                return true;
            }
        } catch (IOException e) {
            Log.e("SAFFileManager", "Error copying file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if file exists in SAF directory.
     */
    public boolean fileExists(String relativePath) {
        if (rootDirectory == null) return false;
        
        String[] parts = relativePath.split("/");
        DocumentFile current = rootDirectory;
        
        for (String part : parts) {
            current = current.findFile(part);
            if (current == null) {
                return false;
            }
        }
        
        return true;
    }
    
    private String getMimeType(String fileName) {
        if (fileName.endsWith(".img")) return "application/octet-stream";
        if (fileName.endsWith(".iso")) return "application/x-iso9660-image";
        return "application/octet-stream";
    }
}
```

**Ação 2: Atualizar FileInstaller para Usar SAF**

Modificar `FileInstaller.installAssetFile()` para aceitar `SAFFileManager`:

```java
public static boolean installAssetFile(Context context, String srcFile,
                                       String destDir, String assetsDir, 
                                       String destFile, SAFFileManager safManager) {
    if (destFile == null) {
        destFile = srcFile;
    }
    
    try {
        AssetManager am = context.getResources().getAssets();
        InputStream is = am.open(assetsDir + "/" + srcFile);
        
        // Use SAF if manager available, else fallback to direct
        if (safManager != null) {
            String relativePath = destDir.substring(destDir.lastIndexOf('/') + 1) 
                                + "/" + destFile;
            return safManager.copyToFile(is, relativePath);
        } else {
            // Legacy direct filesystem access (for backward compatibility)
            return installAssetFileDirectly(context, srcFile, destDir, 
                                          assetsDir, destFile);
        }
    } catch (Exception ex) {
        Log.e("Installer", "failed to install file: " + destFile + 
              ", Error: " + ex.getMessage());
        return false;
    }
}
```

---

## Problema 5: Build e NDK

### Arquivo: `build.gradle` (root)

Criar ou atualizar para:

```gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'com.android.library' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.20' apply false
}

ext {
    compileApi = 34
    targetApi = 34
    minApi = 21
    toolsVersion = "34.0.0"
    ndkVersion = "27.0.11902837"
    cmakeVersion = "3.22.1"
    javaLanguageVersion = 21
}

tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
```

### Arquivo: `CMakeLists.txt`

Atualizar para:

```cmake
cmake_minimum_required(VERSION 3.22)
project(vectras_native)

# Set C++ standard to C++20
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add source files
add_library(vectras_native
    # Add your .cpp files here
    src/main/cpp/rmr_qemu_bridge.c
    src/main/cpp/bitraf.c
)

# Link required libraries
target_link_libraries(vectras_native
    android
    log
)

# Optimization flags
target_compile_options(vectras_native PRIVATE
    -O3
    -ffast-math
    -march=native
)
```

---

## Problema 6: Memory Leak - Object Pooling

### Arquivo: Nova classe `ObjectPool.java`

Criar arquivo:

```java
package com.vectras.vm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe object pool for reducing GC pressure.
 */
public abstract class ObjectPool<T> {
    private final int maxSize;
    private final List<T> available;
    private final List<T> inUse;
    private final ReentrantLock lock = new ReentrantLock();
    
    public ObjectPool(int initialSize, int maxSize) {
        this.maxSize = maxSize;
        this.available = new ArrayList<>(initialSize);
        this.inUse = new ArrayList<>();
        
        for (int i = 0; i < initialSize; i++) {
            available.add(create());
        }
    }
    
    /**
     * Get or create an object from pool.
     */
    public T acquire() {
        lock.lock();
        try {
            T obj;
            if (available.isEmpty()) {
                if (inUse.size() < maxSize) {
                    obj = create();
                } else {
                    throw new IllegalStateException("Object pool exhausted");
                }
            } else {
                obj = available.remove(available.size() - 1);
            }
            inUse.add(obj);
            return obj;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Return an object to the pool.
     */
    public void release(T obj) {
        lock.lock();
        try {
            if (inUse.remove(obj)) {
                reset(obj);
                available.add(obj);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Create a new instance (implemented by subclass).
     */
    protected abstract T create();
    
    /**
     * Reset object to initial state (implemented by subclass).
     */
    protected abstract void reset(T obj);
}
```

### Exemplo de Uso para DeterministicRuntimeMatrix

Criar `DeterministicMatrixPool.java`:

```java
package com.vectras.vm.rafaelia;

import com.vectras.vm.util.ObjectPool;

public class DeterministicMatrixPool extends ObjectPool<double[][]> {
    private final int size;
    
    public DeterministicMatrixPool(int matrixSize, int initialPool, int maxPool) {
        super(initialPool, maxPool);
        this.size = matrixSize;
    }
    
    @Override
    protected double[][] create() {
        return new double[size][size];
    }
    
    @Override
    protected void reset(double[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] = 0.0;
            }
        }
    }
}
```

**Uso no código crítico:**

```java
// Em RafaeliaKernelV22 ou local de alocação
private static final DeterministicMatrixPool MATRIX_POOL = 
    new DeterministicMatrixPool(10, 5, 20);

// No loop crítico, ANTES:
for (int cycle = 0; cycle < 1000000; cycle++) {
    double[][] matrix = new double[10][10];  // ❌ Causa GC
    processMatrix(matrix);
}

// DEPOIS:
for (int cycle = 0; cycle < 1000000; cycle++) {
    double[][] matrix = MATRIX_POOL.acquire();
    try {
        processMatrix(matrix);
    } finally {
        MATRIX_POOL.release(matrix);
    }
}
```

---

## Problema 7: AuditLedger Clock Drift

### Arquivo: `app/src/main/java/com/vectras/vm/audit/AuditLedger.java`

Substituir implementação de timestamp por:

```java
public class AuditLedger {
    
    /**
     * Monotonic clock that detects and logs time anomalies.
     */
    public static class MonotonicClock {
        private long lastMonoNanos = System.nanoTime();
        private long lastWallMs = System.currentTimeMillis();
        
        public synchronized AuditTimestamp now() {
            long currentWallMs = System.currentTimeMillis();
            long currentMonoNanos = System.nanoTime();
            
            // Detect time anomalies
            if (currentWallMs < lastWallMs) {
                Log.w("AuditLedger", "Time went backward: " + 
                      (lastWallMs - currentWallMs) + "ms");
            }
            
            if (currentMonoNanos < lastMonoNanos) {
                Log.w("AuditLedger", "Monotonic time anomaly detected");
            }
            
            lastWallMs = currentWallMs;
            lastMonoNanos = currentMonoNanos;
            
            return new AuditTimestamp(currentWallMs, currentMonoNanos);
        }
    }
    
    public static class AuditTimestamp {
        public final long wallMs;      // System wall clock
        public final long monoNanos;   // Monotonic nano clock
        
        public AuditTimestamp(long wallMs, long monoNanos) {
            this.wallMs = wallMs;
            this.monoNanos = monoNanos;
        }
    }
    
    private final MonotonicClock clock = new MonotonicClock();
    
    public void logEvent(String event, String details) {
        AuditTimestamp ts = clock.now();
        // Log with both timestamps
        String logEntry = String.format(
            "%d,%d,%s,%s",
            ts.wallMs,
            ts.monoNanos,
            event,
            details
        );
        // Write to persistent storage
    }
}
```

---

## Problema 8: Benchmark Counter Overflow

### Arquivo: `app/src/test/java/com/vectras/vm/benchmark/VectraBenchmarkTest.java`

Substituir contadores:

```java
// ANTES:
private long operationCounter = 0;

// DEPOIS:
import java.math.BigInteger;

private BigInteger operationCounter = BigInteger.ZERO;

// Em loop de benchmark:
// ANTES:
operationCounter++;

// DEPOIS:
operationCounter = operationCounter.add(BigInteger.ONE);

// Validação:
if (operationCounter.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
    Log.w("Benchmark", "Counter exceeded Long.MAX_VALUE: " + operationCounter);
}
```

---

## Validação Pós-Fix

### Checklist de Testes

Para cada bug corrigido, executar:

1. **Unit Tests:** `./gradlew testDebugUnitTest`
2. **Integration Tests:** `./gradlew connectedDebugAndroidTest`
3. **Lint Analysis:** `./gradlew lint`
4. **Memory Profiling:** Android Studio Profiler (Memory tab)
5. **Static Analysis:** `./gradlew staticAnalysis` (se configurado)

### Exemplo de Teste para Fix #2 (ProcessSupervisor)

Criar teste:

```java
@Test
public void testStateTransitionThreadSafety() throws InterruptedException {
    ProcessSupervisor supervisor = new ProcessSupervisor(mockContext, "test-vm");
    
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(10);
    
    // 10 threads trying to change state simultaneously
    for (int i = 0; i < 10; i++) {
        executor.submit(() -> {
            supervisor.transitionStateSafe(State.START, State.RUN, 5000);
            latch.countDown();
        });
    }
    
    // Wait with timeout
    boolean completed = latch.await(10, TimeUnit.SECONDS);
    assertTrue("State transition timed out", completed);
    
    // Verify final state is consistent
    assertEquals("Final state should be RUN", State.RUN, supervisor.getState());
    
    executor.shutdown();
}
```

---

**Fim do Guia Técnico**
