# Patches e Correções Específicas
## Vectras-VM-Android v3.6.5 - Código Exato para Remediação de Bugs

**Documento de Referência:** Código de implementação pronto para aplicar  
**Status:** Production-Ready  
**Data:** 14 de fevereiro de 2026  

---

## Correção #1: Credenciais de Assinatura

### Arquivo: app/build.gradle

**Remover (Linhas 34-47):**

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

**Substituir por:**

```gradle
signingConfigs {
    debug {
        storeFile file(findProperty("VECTRAS_STORE_FILE") ?: "../vectras.jks")
        keyAlias findProperty("VECTRAS_KEY_ALIAS") ?: "vectras"
        storePassword findProperty("VECTRAS_STORE_PASSWORD") ?: "debug"
        keyPassword findProperty("VECTRAS_KEY_PASSWORD") ?: "debug"
    }
    release {
        storeFile file(findProperty("VECTRAS_STORE_FILE") ?: "../vectras.jks")
        keyAlias findProperty("VECTRAS_KEY_ALIAS") ?: "vectras"
        storePassword findProperty("VECTRAS_STORE_PASSWORD") ?: ""
        keyPassword findProperty("VECTRAS_KEY_PASSWORD") ?: ""
    }
}
```

### Arquivo: .gitignore

**Adicionar linhas:**

```
local.properties
*.jks
*.keystore
gradle.properties
vectras.jks
```

### Arquivo: local.properties.example (criar novo)

```properties
# Local Configuration - DO NOT COMMIT
# Copy to local.properties and fill with actual values

# Keystore Configuration
VECTRAS_STORE_FILE=../vectras.jks
VECTRAS_KEY_ALIAS=vectras
VECTRAS_STORE_PASSWORD=your_actual_password
VECTRAS_KEY_PASSWORD=your_actual_password

# Android SDK Configuration (optional)
sdk.dir=/path/to/android/sdk
ndk.dir=/path/to/android/ndk
```

---

## Correção #2: Race Condition em ProcessSupervisor

### Arquivo: app/src/main/java/com/vectras/vm/core/ProcessSupervisor.java

**Adicionar após linha 76 (após campo "clock"):**

```java
    // ============ SYNCHRONIZATION LAYER ============
    // Synchronizes state transitions and process access
    private final Object stateLock = new Object();
    private final Object processLock = new Object();
    
    // Tracks last state transition for debugging
    private volatile State lastState = State.START;
    private volatile long lastStateChangeMs = 0;
```

**Adicionar classe interna antes do fechamento da classe (antes de final }):**

```java
    /**
     * Watchdog thread that enforces timeout on blocking operations.
     * If operation exceeds timeout, forces state to STOP and releases locks.
     */
    private static class ProcessWatchdog implements Runnable {
        private final ProcessSupervisor supervisor;
        private final long timeoutMs;
        private volatile boolean cancelled = false;
        private final String threadName;
        
        ProcessWatchdog(ProcessSupervisor supervisor, long timeoutMs) {
            this.supervisor = supervisor;
            this.timeoutMs = timeoutMs;
            this.threadName = "ProcessWatchdog-" + supervisor.vmId;
        }
        
        @Override
        public void run() {
            Thread.currentThread().setName(threadName);
            try {
                Thread.sleep(timeoutMs);
                if (!cancelled) {
                    handleTimeout();
                }
            } catch (InterruptedException e) {
                // Expected when process completes before timeout
                Thread.currentThread().interrupt();
            }
        }
        
        private void handleTimeout() {
            synchronized (supervisor.stateLock) {
                if (supervisor.state != State.STOP) {
                    supervisor.lastState = supervisor.state;
                    supervisor.state = State.STOP;
                    supervisor.lastStateChangeMs = supervisor.clock.wallMs();
                    
                    // Force cleanup
                    if (supervisor.process != null) {
                        try {
                            supervisor.process.destroyForcibly();
                        } catch (Exception e) {
                            Log.e("ProcessWatchdog", "Force kill failed: " + e.getMessage());
                        }
                    }
                    supervisor.process = null;
                    
                    // Notify all waiting threads
                    supervisor.stateLock.notifyAll();
                }
            }
        }
        
        void cancel() {
            cancelled = true;
        }
    }

    /**
     * Thread-safe state transition with precondition checking.
     * @return true if transition completed, false if precondition failed
     */
    public boolean transitionStateSafe(State expectedCurrent, State newState, 
                                       long timeoutMs) {
        synchronized (stateLock) {
            // Verify precondition
            if (state != expectedCurrent) {
                return false;
            }
            
            // Perform transition
            lastState = state;
            state = newState;
            lastStateChangeMs = clock.wallMs();
            
            // Log transition
            transitionSink.onTransition(lastState, newState, "automatic", 
                                       "state_transition", 0, 0, 0);
            
            // Notify all waiting threads
            stateLock.notifyAll();
            
            return true;
        }
    }

    /**
     * Wait for specific state with timeout.
     * @return true if state reached within timeout, false if timeout
     */
    public boolean waitForState(State targetState, long timeoutMs) 
            throws InterruptedException {
        synchronized (stateLock) {
            long endTime = System.currentTimeMillis() + timeoutMs;
            
            while (state != targetState) {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                stateLock.wait(Math.min(remaining, 1000L));
            }
            return true;
        }
    }

    /**
     * Start watchdog thread with timeout enforcement.
     */
    public ProcessWatchdog startWatchdog(long timeoutMs) {
        ProcessWatchdog watchdog = new ProcessWatchdog(this, timeoutMs);
        Thread watchdogThread = new Thread(watchdog, "ProcessWatchdog-" + vmId);
        watchdogThread.setDaemon(true);
        watchdogThread.start();
        return watchdog;
    }
```

**Refatorar qualquer método que modifica "state" para usar sincronização. Exemplo padrão:**

```java
// OLD (not synchronized)
private void setState(State newState) {
    state = newState;
}

// NEW (synchronized)
private void setState(State newState) {
    synchronized (stateLock) {
        lastState = state;
        state = newState;
        lastStateChangeMs = clock.wallMs();
        stateLock.notifyAll();
    }
}
```

---

## Correção #3: Race Condition em FileInstaller

### Arquivo: app/src/main/java/com/vectras/qemu/utils/FileInstaller.java

**Adicionar imports no início do arquivo (após package declaration):**

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
```

**Adicionar após declaração da classe FileInstaller:**

```java
    /**
     * Global file locking mechanism to prevent concurrent writes.
     * Uses ReadWriteLock to allow concurrent reads but exclusive writes.
     */
    private static final Map<String, ReadWriteLock> FILE_LOCKS = 
        Collections.synchronizedMap(new HashMap<>());

    /**
     * Get or create a lock for specific file path.
     */
    private static ReadWriteLock getLockForFile(String filePath) {
        return FILE_LOCKS.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
    }

    /**
     * Atomic write operation: write to temp file, then rename.
     * This prevents partial file writes during concurrent access.
     */
    private static boolean atomicWrite(String targetPath, InputStream source) 
            throws IOException {
        File tempFile = new File(targetPath + ".tmp");
        
        try (OutputStream os = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) > 0) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
        
        // Atomic rename
        File finalFile = new File(targetPath);
        if (!tempFile.renameTo(finalFile)) {
            // Fallback: delete target and retry
            finalFile.delete();
            if (!tempFile.renameTo(finalFile)) {
                Log.e("FileInstaller", "Failed to rename temp file to: " + targetPath);
                return false;
            }
        }
        return true;
    }
```

**Substituir método installAssetFile (linhas 123-150) por:**

```java
    public static boolean installAssetFile(Context context, String srcFile,
                                           String destDir, String assetsDir, String destFile) {
        return installAssetFile(context, srcFile, destDir, assetsDir, destFile, null);
    }

    public static boolean installAssetFile(Context context, String srcFile,
                                           String destDir, String assetsDir, 
                                           String destFile, SAFFileManager safManager) {
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
                // Use SAF if available, fallback to direct filesystem
                if (safManager != null) {
                    String relativePath = new File(destDir).getName() + "/" + destFile;
                    return safManager.copyToFile(is, relativePath);
                } else {
                    // Ensure directory exists
                    File destDirF = new File(destDir);
                    if (!destDirF.exists()) {
                        boolean mkdirResult = destDirF.mkdirs();
                        if (!mkdirResult) {
                            Log.e("FileInstaller", "Could not create directory: " + destDir);
                            if (context instanceof Activity activity) {
                                UIUtils.toastShort(activity, "Could not create directory");
                            }
                            return false;
                        }
                    }
                    
                    // Atomic write with temp file
                    return atomicWrite(fullPath, is);
                }
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w("FileInstaller", "Error closing input stream: " + e.getMessage());
                }
            }
        } catch (Exception ex) {
            Log.e("FileInstaller", "Failed to install file: " + destFile + 
                  ", Error: " + ex.getMessage(), ex);
            return false;
        } finally {
            fileLock.writeLock().unlock();
        }
    }
```

**Refatorar método installFiles para usar locks (substituir loops):**

```java
        for (int k = 0; k < subfiles.length; k++) {
            File file = new File(destDir, files[i] + "/" + subfiles[k]);
            String filePath = file.getAbsolutePath();
            ReadWriteLock fileLock = getLockForFile(filePath);
            
            // Double-check pattern: read lock first
            fileLock.readLock().lock();
            try {
                boolean fileExists = file.exists();
                if (!fileExists || force) {
                    // Need to write: upgrade to write lock
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
                try {
                    fileLock.readLock().unlock();
                } catch (IllegalMonitorStateException e) {
                    // Lock already released by write lock upgrade
                }
            }
        }
```

---

## Correção #4: Android 15 SAF Compatibility

### Arquivo: app/src/main/java/com/vectras/qemu/utils/SAFFileManager.java (NOVO)

**Criar novo arquivo com conteúdo:**

```java
package com.vectras.qemu.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Scoped Access Framework (SAF) abstraction layer.
 * Provides Android 15-compatible file access without direct filesystem paths.
 * 
 * Usage:
 *   1. In Activity, call requestDirectoryAccess()
 *   2. In onActivityResult, call setRootDirectoryUri(data.getData())
 *   3. Use copyToFile() or getOrCreateFile() for file operations
 */
public class SAFFileManager {
    private static final String TAG = "SAFFileManager";
    
    private final Context context;
    private DocumentFile rootDirectory;
    private Uri treeUri;
    
    public static final int REQUEST_DIRECTORY_ACCESS = 1001;
    
    public SAFFileManager(Context context) {
        this.context = context;
    }
    
    /**
     * Initiate directory selection dialog.
     * Call this from Activity and handle result in onActivityResult().
     */
    public Intent createDirectoryPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                       Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return intent;
    }
    
    /**
     * Set root directory with persistent permissions.
     * @param treeUri URI obtained from ACTION_OPEN_DOCUMENT_TREE
     */
    public void setRootDirectoryUri(Uri treeUri) {
        if (treeUri == null) {
            Log.e(TAG, "Tree URI is null");
            return;
        }
        
        this.treeUri = treeUri;
        this.rootDirectory = DocumentFile.fromTreeUri(context, treeUri);
        
        if (this.rootDirectory == null) {
            Log.e(TAG, "Failed to create DocumentFile from tree URI");
            return;
        }
        
        // Grant persistent permissions
        try {
            context.getContentResolver().takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            Log.v(TAG, "Persistent permissions granted for: " + treeUri);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to get persistent permissions: " + e.getMessage());
        }
    }
    
    /**
     * Check if root directory is set.
     */
    public boolean isInitialized() {
        return rootDirectory != null && rootDirectory.canWrite();
    }
    
    /**
     * Navigate to or create file at path, supporting nested directories.
     * Example: "roms/bios/system.img" creates directories if needed.
     */
    public DocumentFile getOrCreateFile(String relativePath) throws IOException {
        if (!isInitialized()) {
            throw new IllegalStateException("SAFFileManager not initialized");
        }
        
        String[] parts = relativePath.split("/");
        DocumentFile current = rootDirectory;
        
        // Navigate/create directories
        for (int i = 0; i < parts.length - 1; i++) {
            DocumentFile dir = current.findFile(parts[i]);
            if (dir == null) {
                dir = current.createDirectory(parts[i]);
                if (dir == null) {
                    throw new IOException("Failed to create directory: " + parts[i]);
                }
            }
            current = dir;
        }
        
        // Get or create final file
        String fileName = parts[parts.length - 1];
        DocumentFile file = current.findFile(fileName);
        if (file == null) {
            String mimeType = getMimeType(fileName);
            file = current.createFile(mimeType, fileName);
            if (file == null) {
                throw new IOException("Failed to create file: " + fileName);
            }
        }
        
        return file;
    }
    
    /**
     * Copy input stream to file in SAF directory.
     */
    public boolean copyToFile(InputStream source, String relativePath) {
        try {
            DocumentFile file = getOrCreateFile(relativePath);
            if (file == null || !file.canWrite()) {
                Log.e(TAG, "Cannot write to file: " + relativePath);
                return false;
            }
            
            OutputStream out = context.getContentResolver()
                    .openOutputStream(file.getUri());
            if (out == null) {
                Log.e(TAG, "Failed to open output stream for: " + relativePath);
                return false;
            }
            
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = source.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
                Log.v(TAG, "Successfully copied: " + relativePath);
                return true;
            } finally {
                out.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying file: " + relativePath, e);
            return false;
        }
    }
    
    /**
     * Check if file exists in SAF directory.
     */
    public boolean fileExists(String relativePath) {
        if (!isInitialized()) {
            return false;
        }
        
        try {
            String[] parts = relativePath.split("/");
            DocumentFile current = rootDirectory;
            
            for (String part : parts) {
                current = current.findFile(part);
                if (current == null) {
                    return false;
                }
            }
            
            return current.exists();
        } catch (Exception e) {
            Log.e(TAG, "Error checking file existence: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file size in bytes.
     */
    public long getFileSize(String relativePath) {
        try {
            DocumentFile file = getOrCreateFile(relativePath);
            return file.length();
        } catch (IOException e) {
            Log.e(TAG, "Error getting file size: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Delete file from SAF directory.
     */
    public boolean deleteFile(String relativePath) {
        try {
            DocumentFile file = getOrCreateFile(relativePath);
            return file.delete();
        } catch (IOException e) {
            Log.e(TAG, "Error deleting file: " + e.getMessage());
            return false;
        }
    }
    
    private String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        
        if (fileName.endsWith(".img")) return "application/octet-stream";
        if (fileName.endsWith(".iso")) return "application/x-iso9660-image";
        if (fileName.endsWith(".zip")) return "application/zip";
        if (fileName.endsWith(".tar")) return "application/x-tar";
        if (fileName.endsWith(".gz")) return "application/gzip";
        if (fileName.endsWith(".txt")) return "text/plain";
        if (fileName.endsWith(".json")) return "application/json";
        if (fileName.endsWith(".xml")) return "application/xml";
        
        return "application/octet-stream";
    }
    
    /**
     * Get persisted URI for storage in preferences.
     */
    public Uri getPersistedUri() {
        return treeUri;
    }
    
    /**
     * Restore from previously persisted URI.
     */
    public void restoreFromUri(Uri persistedUri) {
        if (persistedUri != null) {
            setRootDirectoryUri(persistedUri);
        }
    }
}
```

### Arquivo: app/src/main/AndroidManifest.xml

**Encontrar e atualizar permissões de arquivo:**

```xml
<!-- REMOVER se presente -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<!-- MANTER OU ADICIONAR -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## Correção #5: Build Configuration Update

### Arquivo: build.gradle (root)

**Remover ou atualizar:**

```gradle
// OLD
plugins {
    id 'com.android.application' version '7.6.0' apply false
}

// NEW
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'com.android.library' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.20' apply false
}

ext {
    compileApi = 35
    targetApi = 35
    minApi = 21
    toolsVersion = "35.0.0"
    ndkVersion = "27.0.11902837"
    cmakeVersion = "3.22.1"
    javaLanguageVersion = 21
}
```

### Arquivo: gradle/wrapper/gradle-wrapper.properties

**Atualizar URL do Gradle:**

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.1.1-all.zip
```

### Arquivo: CMakeLists.txt

**Atualizar versão mínima e padrão C++:**

```cmake
cmake_minimum_required(VERSION 3.22)

project(vectras_native)

# Set C++ standard to C++20 for compatibility with NDK r27
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

# Add your native source files
add_library(vectras_native
    src/main/cpp/rmr_qemu_bridge.c
    src/main/cpp/bitraf.c
)

target_link_libraries(vectras_native
    android
    log
)
```

---

## Correção #6: Memory Pool Implementation

### Arquivo: app/src/main/java/com/vectras/vm/util/ObjectPool.java (NOVO)

```java
package com.vectras.vm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe generic object pool for reducing garbage collection pressure.
 * Maintains pre-allocated instances that can be borrowed and returned.
 * 
 * Usage:
 *   MyObject obj = pool.acquire();
 *   try { obj.doWork(); }
 *   finally { pool.release(obj); }
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
        
        // Pre-allocate initial objects
        for (int i = 0; i < initialSize; i++) {
            available.add(create());
        }
    }
    
    /**
     * Acquire object from pool or create new one if pool exhausted.
     */
    public T acquire() {
        lock.lock();
        try {
            T obj;
            if (available.isEmpty()) {
                if (inUse.size() < maxSize) {
                    obj = create();
                } else {
                    throw new IllegalStateException(
                        "Object pool exhausted: " + inUse.size() + "/" + maxSize);
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
     * Return object to pool for reuse.
     */
    public void release(T obj) {
        if (obj == null) return;
        
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
     * Get current pool statistics.
     */
    public PoolStats getStats() {
        lock.lock();
        try {
            return new PoolStats(available.size(), inUse.size(), maxSize);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Create new instance (implemented by subclass).
     */
    protected abstract T create();
    
    /**
     * Reset instance to initial state (implemented by subclass).
     */
    protected abstract void reset(T obj);
    
    public static class PoolStats {
        public final int available;
        public final int inUse;
        public final int maxSize;
        
        public PoolStats(int available, int inUse, int maxSize) {
            this.available = available;
            this.inUse = inUse;
            this.maxSize = maxSize;
        }
    }
}
```

### Arquivo: app/src/main/java/com/vectras/vm/rafaelia/DeterministicMatrixPool.java (NOVO)

```java
package com.vectras.vm.rafaelia;

import com.vectras.vm.util.ObjectPool;

/**
 * Object pool specifically for double[][] matrices.
 * Reduces garbage collection pressure in hot paths.
 */
public class DeterministicMatrixPool extends ObjectPool<double[][]> {
    private final int matrixSize;
    
    public DeterministicMatrixPool(int matrixSize, int initialPoolSize, int maxPoolSize) {
        super(initialPoolSize, maxPoolSize);
        this.matrixSize = matrixSize;
    }
    
    @Override
    protected double[][] create() {
        return new double[matrixSize][matrixSize];
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

---

## Correção #7: AuditLedger Monotonic Clock

### Arquivo: app/src/main/java/com/vectras/vm/audit/AuditLedger.java

**Adicionar classes internas:**

```java
    /**
     * Timestamp with both wall clock and monotonic time.
     * Wall clock is used for user display, monotonic is used for ordering.
     */
    public static class AuditTimestamp {
        public final long wallMs;        // System wall clock (can go backward)
        public final long monoNanos;     // Monotonic nanoseconds (always forward)
        public final boolean anomaly;    // True if time anomaly detected
        
        public AuditTimestamp(long wallMs, long monoNanos, boolean anomaly) {
            this.wallMs = wallMs;
            this.monoNanos = monoNanos;
            this.anomaly = anomaly;
        }
    }

    /**
     * Monotonic clock detector that logs time anomalies.
     */
    public static class MonotonicClock {
        private long lastMonoNanos = System.nanoTime();
        private long lastWallMs = System.currentTimeMillis();
        private final Object lock = new Object();
        
        public AuditTimestamp now() {
            synchronized (lock) {
                long currentWallMs = System.currentTimeMillis();
                long currentMonoNanos = System.nanoTime();
                boolean anomaly = false;
                
                // Detect wall clock anomalies
                if (currentWallMs < lastWallMs) {
                    long drift = lastWallMs - currentWallMs;
                    Log.w("AuditLedger", 
                        "Time went backward: " + drift + "ms (NTP/manual adjustment?)");
                    anomaly = true;
                }
                
                // Detect monotonic clock anomalies (rare, system level)
                if (currentMonoNanos < lastMonoNanos) {
                    Log.e("AuditLedger", 
                        "CRITICAL: Monotonic clock anomaly (system level)");
                    anomaly = true;
                }
                
                lastWallMs = currentWallMs;
                lastMonoNanos = currentMonoNanos;
                
                return new AuditTimestamp(currentWallMs, currentMonoNanos, anomaly);
            }
        }
    }

    private final MonotonicClock clock = new MonotonicClock();

    /**
     * Log event with timestamp anomaly detection.
     */
    public void logEvent(String event, String details) {
        AuditTimestamp ts = clock.now();
        
        if (ts.anomaly) {
            Log.w("AuditLedger", "ANOMALY DETECTED during event: " + event);
        }
        
        String logEntry = String.format(
            "%d|%d|%s|%s|%s",
            ts.wallMs,
            ts.monoNanos / 1_000_000,  // Convert nanos to millis for readability
            ts.anomaly ? "ANOMALY" : "OK",
            event,
            details
        );
        
        // Write to persistent storage (file, database, etc)
        writeLogEntry(logEntry);
    }

    private void writeLogEntry(String entry) {
        // Implementation: write to file, database, or logging framework
        Log.d("AuditLedger", entry);
    }
```

---

## Correção #8: Benchmark Counter Overflow

### Arquivo: app/src/test/java/com/vectras/vm/benchmark/VectraBenchmarkTest.java

**Adicionar import:**

```java
import java.math.BigInteger;
```

**Localizar campo de contador:**

```java
// OLD
private long operationCounter = 0;

// NEW
private BigInteger operationCounter = BigInteger.ZERO;
```

**Localizar incrementos de contador:**

```java
// OLD
operationCounter++;
operationCounter += batchSize;

// NEW
operationCounter = operationCounter.add(BigInteger.ONE);
operationCounter = operationCounter.add(BigInteger.valueOf(batchSize));
```

**Adicionar validação antes de final:**

```java
@After
public void validateResults() {
    if (operationCounter.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
        Log.w("Benchmark", 
            "Counter exceeded Long.MAX_VALUE: " + operationCounter);
    }
}
```

---

## Validação Pós-Aplicação

### Teste de Compilação

```bash
./gradlew clean compileDebugJava
```

### Teste de Build Completo

```bash
./gradlew assembleDebug
```

### Teste de Unit Tests

```bash
./gradlew testDebugUnitTest --info
```

### Teste de Lint

```bash
./gradlew lint --info
```

---

**FIM DO DOCUMENTO DE PATCHES**
