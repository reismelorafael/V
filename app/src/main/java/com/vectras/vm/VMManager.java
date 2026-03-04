package com.vectras.vm;

import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static com.vectras.vm.utils.FileUtils.isFileExists;

import android.androidVNC.ConnectionBean;
import android.androidVNC.VncCanvasActivity;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.qemu.MainVNCActivity;
import com.vectras.qemu.VNCConfig;
import com.vectras.qemu.utils.QmpClient;
import com.vectras.vm.main.MainActivity;
import com.vectras.vm.core.ProcessSupervisor;
import com.vectras.vm.core.VmFlowState;
import com.vectras.vm.core.VmFlowTracker;
import com.vectras.vm.core.ProcessRuntimeOps;
import com.vectras.vm.core.ProcessBudgetRegistry;
import com.vectras.vm.main.core.MainStartVM;
import com.vectras.vm.rafaelia.RafaeliaEventRecorder;
import com.vectras.vm.settings.VNCSettingsActivity;
import com.vectras.vm.settings.X11DisplaySettingsActivity;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.JSONUtils;
import com.vectras.vm.utils.UIUtils;
import com.vectras.vterm.Terminal;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.io.Writer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * VMManager concentra o ciclo de vida de VMs, persistência de configuração
 * e supervisão de processos QEMU em ambiente Android.
 *
 * <p>Este ponto de entrada mantém operações de cadastro/edição de VMs,
 * serialização de metadados em JSON e integração com {@link ProcessSupervisor}
 * para parada previsível com fallback controlado.</p>
 */
public class VMManager {

    public static final String TAG = "VMManager";
    public static volatile String finalJson = "";
    public static String pendingDeviceID = "";
    public static String generatedVMId = "";
    public static int restoredVMs = 0;
    public static boolean isKeptSomeFiles = false;
    public static boolean isQemuStopedWithError = false;
    public static boolean isTryAgain = false;
    public static String latestUnsafeCommandReason = "";
    public static String lastQemuCommand = "";
    private static final ConcurrentHashMap<String, ProcessSupervisor> SUPERVISORS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ProcessBudgetRegistry.SlotToken> SUPERVISOR_SLOTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, VmRuntimeState> VM_STATES = new ConcurrentHashMap<>();
    private static final AtomicLong UNKNOWN_VM_SEQUENCE = new AtomicLong(1L);
    private static final Pattern SAFE_COMMAND_CHARS = Pattern.compile("^[a-zA-Z0-9_./,:=+\\-\"' ]+$");

    public static String getFinalJson() {
        return finalJson;
    }

    private static synchronized void setFinalJson(String value) {
        finalJson = value == null ? "" : value;
    }

    private enum VmRuntimeState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }

    private static String normalizeVmLifecycleId(String vmId) {
        if (vmId == null) return "unknown";
        String normalized = vmId.trim();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    public static synchronized boolean tryMarkVmStarting(String vmId) {
        String key = normalizeVmLifecycleId(vmId);
        pruneInactiveSupervisors();

        VmRuntimeState state = VM_STATES.getOrDefault(key, VmRuntimeState.STOPPED);
        ProcessSupervisor supervisor = SUPERVISORS.get(key);

        if (state == VmRuntimeState.STARTING || state == VmRuntimeState.STOPPING) {
            return false;
        }

        if (supervisor != null && supervisor.isProcessAlive()) {
            VM_STATES.put(key, VmRuntimeState.RUNNING);
            return false;
        }

        VM_STATES.put(key, VmRuntimeState.STARTING);
        return true;
    }

    public static synchronized void clearVmStarting(String vmId) {
        String key = normalizeVmLifecycleId(vmId);
        ProcessSupervisor supervisor = SUPERVISORS.get(key);
        VmRuntimeState state = VM_STATES.getOrDefault(key, VmRuntimeState.STOPPED);

        if (state == VmRuntimeState.STARTING && (supervisor == null || !supervisor.isProcessAlive())) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
        }
    }

    public static synchronized void unregisterVmProcess(String vmId, Process process) {
        String key = normalizeVmLifecycleId(vmId);
        ProcessSupervisor supervisor = SUPERVISORS.get(key);
        if (supervisor == null) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            ProcessBudgetRegistry.releaseByProcess(process, key, ProcessRuntimeOps.safePid(process));
            return;
        }

        if (process != null && !supervisor.isBoundTo(process)) {
            return;
        }

        SUPERVISORS.remove(key, supervisor);
        VM_STATES.put(key, VmRuntimeState.STOPPED);
        ProcessBudgetRegistry.releaseByProcess(process, key, ProcessRuntimeOps.safePid(process));
    }

    public static synchronized void unregisterVmProcess(String vmId) {
        unregisterVmProcess(vmId, null);
    }

    /**
     * Registra o processo da VM no supervisor associado ao identificador.
     *
     * @param context contexto Android para trilha de auditoria
     * @param vmId identificador da VM (nulo/vazio cai para {@code unknown}; IDs transitórios são preservados)
     * @param process processo QEMU ativo
     */
    public static synchronized void registerVmProcess(Context context, String vmId, Process process) {
        if (process == null) return;
        long processPid = ProcessRuntimeOps.safePid(process);
        int maxBudget = getMaxSupervisedVmProcesses();
        ProcessBudgetRegistry.BudgetToken budgetToken = ProcessBudgetRegistry.acquire(
                "vm_process",
                "register",
                resolveBudgetCaller(),
                vmId,
                processPid,
                maxBudget
        );
        if (budgetToken == null) {
            safeTerminateDetachedProcess(process);
            Log.w(TAG, "registerVmProcess rejected: budget acquire denied (" + maxBudget + ") vmId=" + vmId);
            return;
        }
        String key = normalizeVmLifecycleId(vmId);

        if ("unknown".equals(key)) {
            long pid = ProcessRuntimeOps.safePid(process);
            if (pid > 0L) {
                key = "unknown-pid-" + pid;
            } else {
                Log.w(TAG, "registerVmProcess: PID unavailable for unknown vmId, using sequence fallback");
                key = "unknown-seq-" + UNKNOWN_VM_SEQUENCE.getAndIncrement();
            }
        }

        pruneInactiveSupervisors();

        ProcessSupervisor current = SUPERVISORS.get(key);
        VmRuntimeState state = VM_STATES.getOrDefault(key, VmRuntimeState.STOPPED);
        if (current != null && current.isBoundTo(process) && current.isProcessAlive()) {
            VM_STATES.put(key, VmRuntimeState.RUNNING);
            VmFlowTracker.mark(context, key, VmFlowState.RUNNING, "process_already_bound", "run");
            ProcessBudgetRegistry.release(budgetToken, key, processPid);
            return;
        }

        if (state == VmRuntimeState.STARTING || state == VmRuntimeState.STOPPING) {
            if (current != null && current.isProcessAlive()) {
                safeTerminateDetachedProcess(process);
                Log.w(TAG, "registerVmProcess rejected: vm lifecycle busy for key=" + key + " state=" + state);
                ProcessBudgetRegistry.release(budgetToken, key, processPid);
                return;
            }
            VM_STATES.put(key, VmRuntimeState.STOPPED);
        }

        VM_STATES.put(key, VmRuntimeState.STARTING);

        ProcessSupervisor previous = SUPERVISORS.remove(key);
        if (previous != null) {
            if (previous.isBoundTo(process) && previous.isProcessAlive()) {
                SUPERVISORS.put(key, previous);
                VM_STATES.put(key, VmRuntimeState.RUNNING);
                ProcessBudgetRegistry.release(budgetToken, key, processPid);
                return;
            }
            previous.stopGracefully(false);
        }

        if (!ensureSupervisorCapacity()) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            safeTerminateDetachedProcess(process);
            Log.w(TAG, "registerVmProcess rejected: active supervisor cap reached (" + getMaxSupervisedVmProcesses() + ")");
            ProcessBudgetRegistry.release(budgetToken, key, processPid);
            return;
        }

        ProcessBudgetRegistry.SlotToken slot = ProcessBudgetRegistry.get().tryAcquireSlot(
                "vm_process",
                "vm_manager",
                "VMManager.registerVmProcess",
                key
        );
        if (slot == null) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            safeTerminateDetachedProcess(process);
            Log.w(TAG, "registerVmProcess rejected: process budget full key=" + key);
            return;
        }

        ProcessSupervisor supervisor = new ProcessSupervisor(context, key);
        try {
            supervisor.bindProcess(process);
            ProcessBudgetRegistry.get().bindProcess(slot, process);
            SUPERVISORS.put(key, supervisor);
            SUPERVISOR_SLOTS.put(key, slot);
            VM_STATES.put(key, VmRuntimeState.RUNNING);
            ProcessBudgetRegistry.bind(budgetToken, process, key, processPid);
            VmFlowTracker.mark(context, key, VmFlowState.RUNNING, "process_bound", "run");
            spawnProcessExitWatcher(key, supervisor, process);
        } catch (RuntimeException registerError) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            ProcessBudgetRegistry.get().releaseSlot(slot, "register_exception");
            safeTerminateDetachedProcess(process);
            ProcessBudgetRegistry.release(budgetToken, key, processPid);
            String errorMessage = "registerVmProcess recoverable failure: key=" + key
                + " vmId=" + vmId
                + " message=" + registerError.getMessage();
            Log.e(TAG, errorMessage, registerError);
            RafaeliaEventRecorder.recordRecoverable(context, "vm_register_process", errorMessage);
            return;
        }
    }

    private static void spawnProcessExitWatcher(String key, ProcessSupervisor supervisor, Process process) {
        Thread watcher = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cleanupExitedSupervisor(key, supervisor, process);
            }
        }, "vm-exit-watch-" + key);
        watcher.setDaemon(true);
        watcher.start();
    }

    private static synchronized void cleanupExitedSupervisor(String key, ProcessSupervisor supervisor, Process process) {
        ProcessSupervisor active = SUPERVISORS.get(key);
        if (active == null || active != supervisor || !active.isBoundTo(process)) {
            return;
        }
        SUPERVISORS.remove(key, supervisor);
        VM_STATES.put(key, VmRuntimeState.STOPPED);
        ProcessBudgetRegistry.releaseByProcess(process, key, ProcessRuntimeOps.safePid(process));
    }

    private static String resolveBudgetCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack == null || stack.length == 0) {
            return "unknown_caller";
        }
        for (StackTraceElement frame : stack) {
            if (frame == null) continue;
            String className = frame.getClassName();
            if (className == null) continue;
            if (className.startsWith("java.lang.Thread")) continue;
            if (className.equals(VMManager.class.getName())) continue;
            if (className.equals(ProcessBudgetRegistry.class.getName())) continue;
            return className + "#" + frame.getMethodName();
        }
        return "unknown_caller";
    }

    private static void pruneInactiveSupervisors() {
        for (String key : SUPERVISORS.keySet()) {
            ProcessSupervisor supervisor = SUPERVISORS.get(key);
            if (supervisor == null) continue;
            if (!supervisor.isProcessAlive() || supervisor.getState() == ProcessSupervisor.State.STOP) {
                SUPERVISORS.remove(key, supervisor);
                VM_STATES.put(key, VmRuntimeState.STOPPED);
                releaseSlotForKey(key, "prune_inactive");
            }
        }
    }

    private static boolean ensureSupervisorCapacity() {
        if (SUPERVISORS.size() < getMaxSupervisedVmProcesses()) {
            return true;
        }

        pruneInactiveSupervisors();
        if (SUPERVISORS.size() < getMaxSupervisedVmProcesses()) {
            return true;
        }

        String oldestKey = null;
        ProcessSupervisor oldest = null;
        long oldestStart = Long.MAX_VALUE;
        for (Map.Entry<String, ProcessSupervisor> entry : SUPERVISORS.entrySet()) {
            ProcessSupervisor supervisor = entry.getValue();
            if (supervisor == null) continue;
            long start = supervisor.getStartMonoMs();
            if (oldest == null || start < oldestStart) {
                oldestKey = entry.getKey();
                oldest = supervisor;
                oldestStart = start;
            }
        }

        if (oldest != null) {
            oldest.stopGracefully(false);
            SUPERVISORS.remove(oldestKey, oldest);
            if (oldestKey != null) {
                VM_STATES.put(oldestKey, VmRuntimeState.STOPPED);
                releaseSlotForKey(oldestKey, "capacity_eviction");
            }
        }

        return SUPERVISORS.size() < getMaxSupervisedVmProcesses();
    }

    public static synchronized int getActiveSupervisedVmProcessCount() {
        pruneInactiveSupervisors();
        return SUPERVISORS.size();
    }

    public static synchronized int getMaxSupervisedVmProcesses() {
        return ProcessBudgetRegistry.getMaxSupervisedVmProcesses();
    }

    public static synchronized boolean canRegisterAnotherVmProcess() {
        pruneInactiveSupervisors();
        return SUPERVISORS.size() < getMaxSupervisedVmProcesses();
    }

    private static void safeTerminateDetachedProcess(Process process) {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(1200, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(600, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /**
     * Solicita parada do processo da VM com tentativa opcional de desligamento via QMP.
     *
     * @param context contexto Android para trilha de auditoria
     * @param vmId identificador da VM
     * @param tryQmp quando true, tenta desligamento limpo antes de TERM/KILL
     * @return true quando a VM é finalizada dentro dos timeouts de failover e removida do registro ativo
     */
    public static synchronized boolean stopVmProcess(Context context, String vmId, boolean tryQmp) {
        String key = normalizeVmLifecycleId(vmId);
        pruneInactiveSupervisors();
        ProcessSupervisor supervisor = SUPERVISORS.get(key);
        if (supervisor == null) {
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            return false;
        }

        if (!supervisor.isProcessAlive()) {
            SUPERVISORS.remove(key, supervisor);
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            releaseSlotForKey(key, "stop_already_dead");
            return true;
        }

        VM_STATES.put(key, VmRuntimeState.STOPPING);
        VmFlowTracker.mark(context, key, VmFlowState.STOPPING, "stop_requested", tryQmp ? "qmp_term_kill" : "term_kill");
        boolean stopped = supervisor.stopGracefully(tryQmp);
        if (stopped) {
            SUPERVISORS.remove(key, supervisor);
            VM_STATES.put(key, VmRuntimeState.STOPPED);
            releaseSlotForKey(key, "stop_success");
            VmFlowTracker.mark(context, key, VmFlowState.STOPPED, "stop_success", "stopped");
        } else {
            boolean stillAlive = supervisor.isProcessAlive();
            VM_STATES.put(key, stillAlive ? VmRuntimeState.RUNNING : VmRuntimeState.STOPPED);
            VmFlowTracker.mark(context, key, stillAlive ? VmFlowState.RUNNING : VmFlowState.ERROR, stillAlive ? "stop_failed_process_alive" : "stop_failed_unknown", "observe");
        }
        return stopped;
    }

    public static boolean isVMExist(String vmId) {
        String vmJsonListContent = FileUtils.readAFile(AppConfig.romsdatajson);

        if (!JSONUtils.isValidFromString(vmJsonListContent) || vmId.isEmpty()) return false;

        ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(vmJsonListContent, new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList == null) return false;

        for (int _repeat = 0; _repeat < vmList.size(); _repeat++) {
            if (vmList.get(_repeat).containsKey("vmID")
                    && Objects.requireNonNull(vmList.get(_repeat).get("vmID")).toString().equals(vmId)) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "isVMExist: " + vmId + " - YES.");
                }
                return true;
            }
        }

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "isVMExist: " + vmId + " - NO.");
        }
        return false;
    }

    public static boolean addToVMList(String vmConfigJson, String vmID) {
        String vmListJson = FileUtils.readAFile(AppConfig.romsdatajson);
        if (!JSONUtils.isValidFromString(vmListJson) || !JSONUtils.isValidFromString(vmConfigJson)) return false;

        ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(vmListJson, new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList == null) return false;

        HashMap<String, Object> vmConfigMap = new Gson().fromJson(vmConfigJson, new TypeToken<HashMap<String, Object>>() {
        }.getType());

        if (!vmID.isEmpty()) {
            generatedVMId = vmID;
            vmConfigMap.put("vmID", generatedVMId);
        }

        vmList.add(0, vmConfigMap);
        return writeToVMList(new Gson().toJson(vmList)) &&
                writeToVMConfig(Objects.requireNonNull(vmConfigMap.get("vmID")).toString(), new Gson().toJson(vmConfigMap));
    }

    public static boolean addToVMList(HashMap<String, Object> vmConfigMap, String vmID) {
        String vmListJson = FileUtils.readAFile(AppConfig.romsdatajson);
        if (!JSONUtils.isValidFromString(vmListJson)) return false;

        ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(vmListJson, new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList == null) return false;

        if (!vmID.isEmpty()) {
            generatedVMId = vmID;
            vmConfigMap.put("vmID", generatedVMId);
        }

        vmList.add(0, vmConfigMap);
        return writeToVMList(new Gson().toJson(vmList)) &&
                writeToVMConfig(Objects.requireNonNull(vmConfigMap.get("vmID")).toString(), new Gson().toJson(vmConfigMap));
    }

    public static boolean replaceToVMList(int postion, String vmId, String vmConfigJson) {
        String vmListJson = FileUtils.readAFile(AppConfig.romsdatajson);
        if (!JSONUtils.isValidFromString(vmListJson) || !JSONUtils.isValidFromString(vmConfigJson)) return false;

        int finalPosition = postion;
        ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(vmListJson, new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList == null) return false;

        HashMap<String, Object> vmConfigMap = new Gson().fromJson(vmConfigJson, new TypeToken<HashMap<String, Object>>() {
        }.getType());

        if (postion == -1) {
            for (int _repeat = 0; _repeat < vmList.size(); _repeat++) {
                if (vmList.get(_repeat).containsKey("vmID")
                        && ((!vmId.isEmpty() && Objects.requireNonNull(vmList.get(_repeat).get("vmID")).toString().equals(vmId)) || Objects.requireNonNull(vmList.get(_repeat).get("vmID")).toString().equals(Objects.requireNonNull(vmConfigMap.get("vmID")).toString()))) {
                    finalPosition = _repeat;
                    break;
                }
            }
        }

        if (finalPosition >= 0 && finalPosition < vmList.size()) {
            vmList.set(finalPosition, vmConfigMap);
        } else {
            return false;
        }

        return writeToVMList(new Gson().toJson(vmList)) &&
                writeToVMConfig(Objects.requireNonNull(vmConfigMap.get("vmID")).toString(), new Gson().toJson(vmConfigMap));
    }

    public static boolean replaceToVMList(int postion, String vmId, HashMap<String, Object> vmConfigMap) {
        String vmListJson = FileUtils.readAFile(AppConfig.romsdatajson);
        if (!JSONUtils.isValidFromString(vmListJson)) return false;

        int finalPosition = postion;
        ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(vmListJson, new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList == null) return false;

        if (postion == -1) {
            for (int _repeat = 0; _repeat < vmList.size(); _repeat++) {
                if (vmList.get(_repeat).containsKey("vmID")
                        && ((!vmId.isEmpty() && Objects.requireNonNull(vmList.get(_repeat).get("vmID")).toString().equals(vmId)) || Objects.requireNonNull(vmList.get(_repeat).get("vmID")).toString().equals(Objects.requireNonNull(vmConfigMap.get("vmID")).toString()))) {
                    finalPosition = _repeat;
                    break;
                }
            }
        }

        if (finalPosition >= 0 && finalPosition < vmList.size()) {
            vmList.set(finalPosition, vmConfigMap);
        } else {
            return false;
        }

        return writeToVMList(new Gson().toJson(vmList)) &&
                writeToVMConfig(Objects.requireNonNull(vmConfigMap.get("vmID")).toString(), new Gson().toJson(vmConfigMap));
    }

    public static boolean writeToVMList(String content) {
        return FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", content);
    }

    public static boolean writeToVMConfig(String vmID, String content) {
        return FileUtils.writeToFile(AppConfig.maindirpath + "/roms/" + vmID, "rom-data.json", content.replace("\\u003d", "=")) &&
                FileUtils.writeToFile(AppConfig.maindirpath + "/roms/" + vmID, "vmID.txt", vmID);
    }

    public static boolean createNewVM(String name, String thumbnail, String drive, String arch, String cdrom, String params, String vmID, int port) {
        HashMap<String, Object> vmConfigMap = new HashMap<>();
        vmConfigMap.put("imgName", name);
        vmConfigMap.put("imgIcon", thumbnail);
        vmConfigMap.put("imgPath", drive);
        vmConfigMap.put("imgCdrom", cdrom);
        vmConfigMap.put("imgExtra", params);
        vmConfigMap.put("imgArch", arch);
        vmConfigMap.put("vmID", vmID);
        vmConfigMap.put("qmpPort", port);

        return addToVMList(vmConfigMap, vmID);
    }

    public static boolean editVM(String name, String thumbnail, String drive, String arch, String cdrom, String params, int position) {
        ArrayList<HashMap<String, Object>> vmList;

        vmList = new Gson().fromJson(FileUtils.readAFile(AppConfig.romsdatajson), new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        HashMap<String, Object> vmConfigMap = new HashMap<>();
        vmConfigMap.put("imgName", name);
        vmConfigMap.put("imgIcon", thumbnail);
        vmConfigMap.put("imgPath", drive);
        vmConfigMap.put("imgCdrom", cdrom);
        vmConfigMap.put("imgExtra", params);
        vmConfigMap.put("imgArch", arch);
        if (!vmList.isEmpty() && vmList.get(position).containsKey("qmpPort")) {
            vmConfigMap.put("qmpPort", vmList.get(position).get("qmpPort"));
        } else {
            vmConfigMap.put("qmpPort", startRandomPort());
        }

        if (!vmList.isEmpty() && vmList.get(position).containsKey("vmID")) {
            vmConfigMap.put("vmID", Objects.requireNonNull(vmList.get(position).get("vmID")).toString());
        } else {
            vmConfigMap.put("vmID", idGenerator());
        }

        return replaceToVMList(position, "", vmConfigMap);
    }

    public static void deleteVMDialog(String _vmName, int _position, Activity _activity) {
        DialogUtils.threeDialog(_activity, _activity.getString(R.string.remove) + " " + _vmName, _activity.getString(R.string.remove_vm_content), _activity.getString(R.string.remove_and_do_not_keep_files), _activity.getString(R.string.remove_but_keep_files), _activity.getString(R.string.cancel), true, R.drawable.delete_24px, true,
                () -> {
                    View progressView = LayoutInflater.from(_activity).inflate(R.layout.dialog_progress_style, null);
                    TextView progress_text = progressView.findViewById(R.id.progress_text);
                    progress_text.setText(_activity.getString(R.string.just_a_moment));
                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(_activity, R.style.CenteredDialogTheme)
                            .setView(progressView)
                            .setCancelable(false)
                            .create();
                    progressDialog.show();

                    new Thread(() -> {
                        isKeptSomeFiles = false;
                        deleteVM(_position);
                        removeInRomsDataJson(_activity, _vmName, _position);
                        _activity.runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(progressDialog::dismiss, 500));
                    }).start();
                },
                () -> {
                    View progressView = LayoutInflater.from(_activity).inflate(R.layout.dialog_progress_style, null);
                    TextView progress_text = progressView.findViewById(R.id.progress_text);
                    progress_text.setText(_activity.getString(R.string.just_a_moment));
                    AlertDialog progressDialog = new MaterialAlertDialogBuilder(_activity, R.style.CenteredDialogTheme)
                            .setView(progressView)
                            .setCancelable(false)
                            .create();
                    progressDialog.show();

                    new Thread(() -> {
                        hideVMIDWithPosition(_position);
                        removeInRomsDataJson(_activity, _vmName, _position);
                        _activity.runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(progressDialog::dismiss, 500));
                    }).start();
                },
                null,
                null);
    }

    public static void removeInRomsDataJson(Activity _activity, String _vmName, int _position) {
        try {
            JSONArray jSONArray = new JSONArray(FileUtils.readFromFile(_activity, new File(AppConfig.maindirpath
                    + "roms-data.json")));
            jSONArray.remove(_position);


            Writer output;
            File jsonFile = new File(AppConfig.maindirpath + "roms-data" + ".json");
            output = new BufferedWriter(new FileWriter(jsonFile));
            output.write(jSONArray.toString());
            output.close();
        } catch (Exception e) {
            UIUtils.toastLong(_activity, e.toString());
        }
        UIUtils.toastLong(_activity, _vmName + _activity.getString(R.string.are_removed_successfully));

        MainActivity.refeshVMListNow();
    }

    public static String idGenerator() {
        final int attempts = 512;
        for (int attempt = 0; attempt < attempts; attempt++) {
            String candidate = startRandomVMID();
            if (!isFileExists(AppConfig.maindirpath + "/roms/" + candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to allocate unique VM id after " + attempts + " attempts");
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @NonNull
    public static String startRandomVMID() {
        StringBuilder result = new StringBuilder(10);

        for (int i = 0; i < 10; i++) {
            if (SECURE_RANDOM.nextBoolean()) {
                result.append((char) ('a' + SECURE_RANDOM.nextInt(26)));
            } else {
                result.append((char) ('0' + SECURE_RANDOM.nextInt(10)));
            }
        }

        return result.toString();
    }

    /** @deprecated Use {@link #startRandomVMID()} instead. */
    @Deprecated
    @NonNull
    public static String startRamdomVMID() {
        return startRandomVMID();
    }

    //This can be removed because QMP currently uses sockets instead of open ports.
    @Deprecated
    public static int startRandomPort() {
        final int min = 10_000;
        final int max = 65_535;
        final int attempts = 128;
        Set<Integer> reservedPorts = readReservedPortsFromVmDb();

        for (int attempt = 0; attempt < attempts; attempt++) {
            int candidate = SECURE_RANDOM.nextInt(max - min + 1) + min;
            if (reservedPorts.contains(candidate)) {
                continue;
            }
            if (isPortAvailable(candidate)) {
                return candidate;
            }
        }

        return 8080;
    }

    private static Set<Integer> readReservedPortsFromVmDb() {
        Set<Integer> ports = new HashSet<>();
        if (!FileUtils.isFileExists(AppConfig.romsdatajson) || !FileUtils.canRead(AppConfig.romsdatajson)) {
            return ports;
        }
        String content = FileUtils.readAFile(AppConfig.romsdatajson);
        if (content == null || content.isEmpty()) {
            return ports;
        }
        java.util.regex.Matcher matcher = Pattern.compile("\\\"qmpPort\\\"\\s*:\\s*(\\d+)").matcher(content);
        while (matcher.find()) {
            try {
                ports.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries and keep scanning.
            }
        }
        return ports;
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            ignored.setReuseAddress(true);
            return true;
        } catch (IOException ioException) {
            return false;
        }
    }

    static ArrayList<HashMap<String, Object>> parseVmListJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ArrayList<HashMap<String, Object>> vmList = new Gson().fromJson(json, new TypeToken<ArrayList<HashMap<String, Object>>>() {
            }.getType());
            return vmList != null ? vmList : new ArrayList<>();
        } catch (RuntimeException parseError) {
            Log.w(TAG, "parseVmListJson: invalid JSON, using empty list", parseError);
            return new ArrayList<>();
        }
    }

    static boolean isValidVmPosition(ArrayList<HashMap<String, Object>> vmList, int position) {
        return vmList != null && position >= 0 && position < vmList.size();
    }

    public static void deleteVM(int position) {
        String vmId;
        ArrayList<HashMap<String, Object>> vmList = parseVmListJson(
                FileUtils.readAFile(AppConfig.maindirpath + "roms-data.json"));

        if (vmList == null || position < 0 || position > vmList.size() - 1) return;

        if (vmList.get(position).containsKey("vmID")) {
            vmId = Objects.requireNonNull(vmList.get(position).get("vmID")).toString();
            FileUtils.deleteDirectory(Config.getCacheDir() + "/" + vmId);
            if (BuildConfig.DEBUG) {
                Log.i("VMManager", "deleteVM: ID obtained: " + vmId);
            }
        } else {
            Log.e("VMManager", "deleteVM: Cannot get ID.");
            return;
        }
        vmList.remove(position);
        setFinalJson(new Gson().toJson(vmList));
        if (!vmId.isEmpty()) {
            int _startRepeat = 0;
            String _currentVMIDToScan;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                            _currentVMIDToScan = FileUtils.readAFile(_filelist.get(_startRepeat) + "/vmID.txt").replace("\n", "");
                            if (!_currentVMIDToScan.isEmpty()) {
                                if (_currentVMIDToScan.equals(vmId)) {
                                    if (!getFinalJson().contains(_filelist.get(_startRepeat))) {
                                        FileUtils.deleteDirectory(_filelist.get(_startRepeat));
                                    } else {
                                        isKeptSomeFiles = true;
                                        hideVMID(vmId);
                                    }
                                }
                            }
                        }
                    }
                    _startRepeat++;
                }
            }
        }
    }

    public static void hideVMID(@NonNull String _vmID) {
        if (!_vmID.isEmpty()) {
            int _startRepeat = 0;
            String _currentVMIDToScan;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                            _currentVMIDToScan = FileUtils.readAFile(_filelist.get(_startRepeat) + "/vmID.txt").replace("\n", "");
                            if (!_currentVMIDToScan.isEmpty()) {
                                if (_currentVMIDToScan.equals(_vmID)) {
                                    FileUtils.moveAFile(_filelist.get(_startRepeat) + "/vmID.txt", _filelist.get(_startRepeat) + "/vmID.old.txt");
                                }
                            }
                        }
                    }
                    _startRepeat++;
                }
            }
        }
    }

    public static void hideVMIDWithPosition(int position) {
        String vmId;
        ArrayList<HashMap<String, Object>> vmList;
        vmList = new Gson().fromJson(FileUtils.readAFile(AppConfig.maindirpath + "roms-data.json"), new TypeToken<ArrayList<HashMap<String, Object>>>() {
        }.getType());

        if (vmList.isEmpty()) return;

        if (vmList.get(position).containsKey("vmID")) {
            vmId = Objects.requireNonNull(vmList.get(position).get("vmID")).toString();
        } else {
            return;
        }
        if (!vmId.isEmpty()) {
            int _startRepeat = 0;
            String _currentVMIDToScan;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                            _currentVMIDToScan = FileUtils.readAFile(_filelist.get(_startRepeat) + "/vmID.txt").replace("\n", "");
                            if (!_currentVMIDToScan.isEmpty()) {
                                if (_currentVMIDToScan.equals(vmId)) {
                                    FileUtils.moveAFile(_filelist.get(_startRepeat) + "/vmID.txt", _filelist.get(_startRepeat) + "/vmID.old.txt");
                                }
                            }
                        }
                    }
                    _startRepeat++;
                }
            }
        }
    }

    public static void cleanUp() {
        setFinalJson(FileUtils.readAFile(AppConfig.romsdatajson));
        String snapshot = getFinalJson();
        if (!snapshot.isEmpty()) {
            int _startRepeat = 0;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (!isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                            if (!snapshot.contains(_filelist.get(_startRepeat))) {
                                FileUtils.deleteDirectory(_filelist.get(_startRepeat));
                            }
                        }
                    }
                    _startRepeat++;
                }
            }
        }
    }

    public static void restoreVMs() {
        int _startRepeat = 0;
        restoredVMs = 0;
        JsonArray restoredEntries = new JsonArray();
        JsonArray mainEntries = readJsonArrayOrEmpty(FileUtils.readAFile(AppConfig.maindirpath + "/roms-data.json"));
        ArrayList<String> _filelist = new ArrayList<>();
        FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
        if (!_filelist.isEmpty()) {
            for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                if (_startRepeat < _filelist.size()) {
                    if (!isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                        if (isFileExists(_filelist.get(_startRepeat) + "/rom-data.json")) {
                            String romDataContent = FileUtils.readAFile(_filelist.get(_startRepeat) + "/rom-data.json");
                            if (JSONUtils.isValidFromString(romDataContent)) {
                                JsonElement candidate = JsonParser.parseString(romDataContent);
                                if (candidate.isJsonObject()) {
                                    mainEntries.add(candidate.deepCopy());
                                    restoredEntries.add(candidate.deepCopy());
                                    if (isFileExists(_filelist.get(_startRepeat) + "/vmID.old.txt")) {
                                        enableVMID(FileUtils.readAFile(_filelist.get(_startRepeat) + "/vmID.old.txt"));
                                    } else {
                                        FileUtils.writeToFile(_filelist.get(_startRepeat), "/vmID.txt", VMManager.idGenerator());
                                    }
                                    restoredVMs++;
                                } else if (BuildConfig.DEBUG) {
                                    Log.i("CqcmActivity", "Ignoring non-object rom-data.json during restore: " + _filelist.get(_startRepeat));
                                }
                            }
                        }
                    }

                    _startRepeat++;
                    if (_startRepeat == _filelist.size()) {
                        if (restoredEntries.size() > 0) {
                            String serialized = new Gson().toJson(mainEntries);
                            if (JSONUtils.isValidFromString(serialized)) {
                                FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", serialized);
                            } else {
                                restoredVMs = 0;
                            }
                        } else {
                            restoredVMs = 0;
                        }
                    }
                }
            }

        }
    }

    static String appendVmEntriesJson(String currentJson, String... candidateJsonEntries) {
        JsonArray base = readJsonArrayOrEmpty(currentJson);
        if (candidateJsonEntries == null) {
            return new Gson().toJson(base);
        }

        for (String candidateJson : candidateJsonEntries) {
            if (!JSONUtils.isValidFromString(candidateJson)) {
                continue;
            }
            JsonElement element = JsonParser.parseString(candidateJson);
            if (!element.isJsonObject()) {
                continue;
            }
            base.add(element.deepCopy());
        }

        return new Gson().toJson(base);
    }

    private static JsonArray readJsonArrayOrEmpty(String jsonContent) {
        if (!JSONUtils.isValidFromString(jsonContent)) {
            return new JsonArray();
        }

        JsonElement parsed = JsonParser.parseString(jsonContent);
        if (!parsed.isJsonArray()) {
            return new JsonArray();
        }

        return parsed.getAsJsonArray().deepCopy();
    }

    public static void startFixRomsDataJson() {
        int _startRepeat = 0;
        String tempRomData;
        JsonArray arr = new JsonArray();
        restoredVMs = 0;
        ArrayList<String> _filelist = new ArrayList<>();
        FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
        if (!_filelist.isEmpty()) {
            for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                if (_startRepeat < _filelist.size()) {
                    if (isFileExists(_filelist.get(_startRepeat) + "/vmID.txt")) {
                        if (isFileExists(_filelist.get(_startRepeat) + "/rom-data.json")) {
                            tempRomData = FileUtils.readAFile(_filelist.get(_startRepeat) + "/rom-data.json");
                            if (JSONUtils.isValidFromString(tempRomData)) {
                                arr.add(JsonParser.parseString(tempRomData));
                                restoredVMs++;
                            }
                        }
                    }

                    _startRepeat++;
                    if (_startRepeat == _filelist.size()) {
                        if (restoredVMs > 0) {
                            FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", arr.toString());
                        }
                    }
                }
            }

        }
    }

    public static void enableVMID(@NonNull String _vmID) {
        if (_vmID.isEmpty())
            return;
        int _startRepeat = 0;
        ArrayList<String> _filelist = new ArrayList<>();
        FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
        if (!_filelist.isEmpty()) {
            for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                if (_startRepeat < _filelist.size()) {
                    if (isFileExists(_filelist.get(_startRepeat) + "/vmID.old.txt")) {
                        if (FileUtils.readAFile(_filelist.get(_startRepeat) + "/vmID.old.txt").equals(_vmID)) {
                            FileUtils.moveAFile(_filelist.get(_startRepeat) + "/vmID.old.txt", _filelist.get(_startRepeat) + "/vmID.txt");
                        }
                    }
                }
                _startRepeat++;
            }
        }
    }

    public static void movetoRecycleBin() {
        File vDir = new File(AppConfig.recyclebin);
        if (!vDir.exists()) {
            if (!vDir.mkdirs()) {
                return;
            }
        }
        setFinalJson(FileUtils.readAFile(AppConfig.romsdatajson));
        String snapshot = getFinalJson();
        if (!snapshot.isEmpty()) {
            int _startRepeat = 0;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(AppConfig.vmFolder, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (!snapshot.contains(Objects.requireNonNull(Uri.parse(_filelist.get(_startRepeat)).getLastPathSegment()))) {
                            FileUtils.moveAFile(_filelist.get(_startRepeat), AppConfig.recyclebin + Uri.parse(_filelist.get(_startRepeat)).getLastPathSegment());
                        }
                    }
                    _startRepeat++;
                }
            }
        }
    }

    public static String quickScanDiskFileInFolder(@NonNull String _foderpath) {
        if (!_foderpath.isEmpty()) {
            int _startRepeat = 0;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(_foderpath, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (isADiskFile(_filelist.get(_startRepeat))) {
                            return _filelist.get(_startRepeat);
                        }
                    }
                    _startRepeat++;
                }
            }
        }
        return "";
    }

    public static boolean isADiskFile(@NonNull String _filepath) {
        if (_filepath.contains(".")) {
            String _getFileName = Objects.requireNonNull(Uri.parse(_filepath).getLastPathSegment()).toLowerCase();
            String _getFileFormat = _getFileName.substring(_getFileName.lastIndexOf(".") + 1);
            return "qcow2,img,vhd,vhdx,vdi,qcow,vmdk,vpc".contains(_getFileFormat);
        }
        return false;
    }

    public static String quickScanISOFileInFolder(@NonNull String _foderpath) {
        if (!_foderpath.isEmpty()) {
            int _startRepeat = 0;
            ArrayList<String> _filelist = new ArrayList<>();
            FileUtils.getAListOfAllFilesAndFoldersInADirectory(_foderpath, _filelist);
            if (!_filelist.isEmpty()) {
                for (int _repeat = 0; _repeat < _filelist.size(); _repeat++) {
                    if (_startRepeat < _filelist.size()) {
                        if (isAISOFile(_filelist.get(_startRepeat))) {
                            return _filelist.get(_startRepeat);
                        }
                    }
                    _startRepeat++;
                }
            }
        }
        return "";
    }

    public static boolean isAISOFile(@NonNull String _filepath) {
        if (_filepath.contains(".")) {
            String _getFileName = Objects.requireNonNull(Uri.parse(_filepath).getLastPathSegment()).toLowerCase();
            String _getFileFormat = _getFileName.substring(_getFileName.lastIndexOf(".") + 1);
            return "iso".contains(_getFileFormat);
        }
        return false;
    }

    public static void setArch(@NonNull String _arch, Activity _activity) {
        switch (_arch) {
            case "I386":
                MainSettingsManager.setArch(_activity, "I386");
                break;
            case "ARM64":
                MainSettingsManager.setArch(_activity, "ARM64");
                break;
            case "PPC":
                MainSettingsManager.setArch(_activity, "PPC");
                break;
            default:
                MainSettingsManager.setArch(_activity, "X86_64");
                break;
        }
    }

    public static boolean isExecutedCommandError(@NonNull String _command, String _result, Context _activity) {
        if (!_command.contains("qemu-system")) {
            isQemuStopedWithError = false;
            return false;
        }

        if (_command.contains("qemu-system") && _result.contains("Killed")) {
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        }
        //Error code: PROOT_IS_MISSING_0
        if (_result.contains("proot\": error=2,")) {
            DialogUtils.twoDialog(_activity, _activity.getResources().getString(R.string.problem_has_been_detected), _activity.getResources().getString(R.string.error_PROOT_IS_MISSING_0), _activity.getString(R.string.continuetext), _activity.getString(R.string.cancel), true, R.drawable.build_24px, true,
                    () -> {
                        MainActivity.isActivate = false;
                        FileUtils.deleteDirectory(_activity.getFilesDir().getAbsolutePath() + "/data");
                        FileUtils.deleteDirectory(_activity.getFilesDir().getAbsolutePath() + "/distro");
                        FileUtils.deleteDirectory(_activity.getFilesDir().getAbsolutePath() + "/usr");
                        Intent intent = new Intent();
                        intent.setClass(_activity, SplashActivity.class);
                        _activity.startActivity(intent);
                    },
                    null, null);
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else if (_result.contains(") exists") && _result.contains("drive with bus")) {
            //Error code: DRIVE_INDEX_0_EXISTS
            DialogUtils.oneDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.error_DRIVE_INDEX_0_EXISTS) + "\n\n" + _result, R.drawable.hard_drive_24px);
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else if (_result.contains("gtk initialization failed") || _result.contains("x11 not available")) {
            //Error code: X11_NOT_AVAILABLE
            DialogUtils.twoDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.error_X11_NOT_AVAILABLE), _activity.getString(R.string.switch_to_vnc), _activity.getString(R.string.cancel), true, R.drawable.cast_24px, true,
                    () -> {
                        MainSettingsManager.setVmUi(_activity, "VNC");
                        DialogUtils.oneDialog(_activity, _activity.getString(R.string.done), _activity.getString(R.string.switched_to_VNC), R.drawable.check_24px);
                    },
                    null, null);
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else if (_result.contains("Couldn't connect to XServer")) {
            if (isTryAgain) {
                DialogUtils.oneDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.x11_display_cannot_be_used_at_this_time_content) + "\n\n" + _result, R.drawable.cast_warning_24px);
                _activity.stopService(new Intent(_activity, MainService.class));
                markQemuStoppedWithError();
                RafaeliaEventRecorder.recordCrash(_activity, _result);
                isTryAgain = false;
            } else {
                MainStartVM.startTryAgain(_activity);
                isTryAgain = true;
            }
            return true;
        } else if (_result.contains("No such file or directory")) {
            //Error code: NO_SUCH_FILE_OR_DIRECTORY
            DialogUtils.oneDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.error_NO_SUCH_FILE_OR_DIRECTORY) + "\n\n" + _result, R.drawable.file_copy_24px);
            _activity.stopService(new Intent(_activity, MainService.class));
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else if (_result.contains("another process using")) {
            //Error code: ANOTHER_PROCESS_USING_IMAGE
            DialogUtils.oneDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.error_ANOTHER_PROCESS_USING_IMAGE) + "\n\n" + _result, R.drawable.file_copy_24px);
            _activity.stopService(new Intent(_activity, MainService.class));
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else if (_result.contains("mesapt: invalid sdl display")) {
            DialogUtils.twoDialog(_activity,
                    _activity.getResources().getString(R.string.problem_has_been_detected),
                    _activity.getResources().getString(R.string.you_need_to_switch_to_sdl_to_use_3dfx),
                    _activity.getString(R.string.go_to_settings),
                    _activity.getString(R.string.close),
                    true,
                    R.drawable.desktop_24px,
                    true,
                    () -> {
                        Intent intent = new Intent();
                        intent.setClass(_activity, X11DisplaySettingsActivity.class);
                        _activity.startActivity(intent);
                    },
                    null, null);
            return false;
        } else if (_command.contains("qemu-system") && _result.contains("qemu-system") && !_result.contains("warning:")) {
            //Error code: UNKNOW_ERROR
            DialogUtils.oneDialog(_activity, _activity.getString(R.string.problem_has_been_detected), _activity.getString(R.string.vm_could_not_be_run_content) + "\n\n" + _result, R.drawable.error_96px);
            _activity.stopService(new Intent(_activity, MainService.class));
            markQemuStoppedWithError();
            RafaeliaEventRecorder.recordCrash(_activity, _result);
            return true;
        } else {
            isQemuStopedWithError = false;
            return false;
        }
    }

    private static void closeVmFdsOnStop() {
        String vmId = MainStartVM.ensureLastVmIdInitialized(MainStartVM.lastVMID);
        FileUtils.closeFdsForVm(vmId);
        com.vectras.qemu.utils.FileUtils.close_fds();
    }

    private static void markQemuStoppedWithError() {
        isQemuStopedWithError = true;
        closeVmFdsOnStop();
    }

    public static boolean isRomsDataJsonValid(Boolean _needfix, Activity _context) {
        if (isFileExists(AppConfig.romsdatajson)) {
            if (!JSONUtils.isValidFromFile(AppConfig.romsdatajson)) {
                if (_needfix) {
                    DialogUtils.twoDialog(_context, _context.getString(R.string.problem_has_been_detected), _context.getString(R.string.need_fix_json_before_create), _context.getString(R.string.continuetext), _context.getString(R.string.cancel), true, R.drawable.build_24px, true,
                            () -> {
                                FileUtils.moveAFile(AppConfig.maindirpath + "roms-data.json", AppConfig.maindirpath + "roms-data.old.json");
                                FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", "[]");
                                startFixRomsDataJson();
                                fixRomsDataJsonResult(_context);
                            },
                            null, null);
                }
                return false;
            } else {
                return true;
            }
        } else {
            FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", "[]");
            return true;
        }
    }

    public static void fixRomsDataJsonResult(Activity _context) {
        DialogUtils.oneDialog(
                _context,
                _context.getString(R.string.done),
                restoredVMs == 0 ? _context.getString(R.string.roms_data_json_fixed_unsuccessfully) : _context.getString(R.string.roms_data_json_fixed_successfully),
                R.drawable.error_96px
        );
        MainActivity.refeshVMListNow();
        movetoRecycleBin();
    }

    public static boolean isthiscommandsafe(@NonNull String _command, Context _context) {
        if (BuildConfig.DEBUG) {
            Log.d("VMManager.isthiscommandsafe", _command);
        }

        String command = _command.trim();
        if (command.isEmpty()) {
            latestUnsafeCommandReason = _context.getString(R.string.not_the_command_to_run_qemu);
            return false;
        }

        if (!SAFE_COMMAND_CHARS.matcher(command).matches()) {
            latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_multiple_lines);
            return false;
        }

        if (command.contains("&&") || command.contains("||") || command.contains("$")
                || command.contains("`") || command.contains("<") || command.contains(">")
                || command.contains("(") || command.contains(")")) {
            latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_semicolons);
            return false;
        }

        if (command.startsWith("qemu")) {
            if (!command.contains("&")) {
                if (!command.contains("\n")) {
                    if (!command.contains(";")) {
                        if (!command.contains("|")) {
                            return true;
                        } else {
                            latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_vertical_bars);
                        }
                    } else {
                        latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_semicolons);
                    }
                } else {
                    latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_multiple_lines);
                }
            } else {
                latestUnsafeCommandReason = _context.getString(R.string.command_are_not_allowed_to_contain_amp);
            }
        } else {
            latestUnsafeCommandReason = _context.getString(R.string.not_the_command_to_run_qemu);
        }
        return false;
    }

    public static boolean isthiscommandsafeimg(@NonNull String _command, Context _context) {
        String command = _command.trim();
        if (!isthiscommandsafe(command, _context)) {
            return false;
        }
        if (!command.contains("qcow2")) {
            String _getsize = command.substring(command.lastIndexOf(" ") + 1);
            if (_getsize.toLowerCase().endsWith("t") || _getsize.toLowerCase().endsWith("p") || _getsize.toLowerCase().endsWith("e")) {
                latestUnsafeCommandReason = _context.getString(R.string.size_too_large_try_qcow2_format);
                return false;
            }
            if (_getsize.toLowerCase().endsWith("g")) {
                if (_getsize.length() <= 2) {
                    return true;
                } else {
                    latestUnsafeCommandReason = _context.getString(R.string.size_too_large_try_qcow2_format);
                    return false;
                }
            }
            if (_getsize.toLowerCase().endsWith("m")) {
                if (_getsize.length() <= 4) {
                    return true;
                } else {
                    latestUnsafeCommandReason = _context.getString(R.string.size_too_large_try_qcow2_format);
                    return false;
                }
            }
            if (_getsize.toLowerCase().endsWith("k")) {
                if (_getsize.length() <= 8) {
                    return true;
                } else {
                    latestUnsafeCommandReason = _context.getString(R.string.size_too_large_try_qcow2_format);
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isVMRunning(Context context, String vmID) {
        String result = Terminal.executeShellCommandWithResult("ps -e", context);
        if (result.contains(Config.getCacheDir() + "/" + vmID + "/qmpsocket")) {
            if (BuildConfig.DEBUG) {
                Log.d("VMManager.isThisVMRunning", "Yes");
            }
            return true;
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("VMManager.isThisVMRunning", "No");
            }
            return false;
        }
    }

    public static boolean isQemuRunning(Activity activity) {
        Terminal vterm = new Terminal(activity);
        vterm.executeShellCommand2("ps -e", false, activity);
        if (AppConfig.temporaryLastedTerminalOutput.contains("qemu-system")) {
            if (BuildConfig.DEBUG) {
                Log.d("VMManager.isQemuRunning", "Yes");
            }
            return true;
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("VMManager.isQemuRunning", "No");
            }
            return false;
        }
    }

    public static boolean isHaveADisk(String env) {
        return env.contains("-drive") || env.contains("-hda") || env.contains("-hdb") || env.contains("-cdrom") || env.contains("-fda") || env.contains("-fdb");
    }

    public static void setIconWithName(ImageView imageview, String name) {
        String itemName = name.toLowerCase();
        if (itemName.contains("linux") || itemName.contains("ubuntu") || itemName.contains("debian") || itemName.contains("arch") || itemName.contains("kali")) {
            imageview.setImageResource(R.drawable.linux);
        } else if (itemName.contains("windows")) {
            imageview.setImageResource(R.drawable.windows);
        } else if (itemName.contains("macos") || itemName.contains("mac os")) {
            imageview.setImageResource(R.drawable.macos);
        } else if (itemName.contains("android")) {
            imageview.setImageResource(R.drawable.android);
        } else {
            imageview.setImageResource(R.drawable.ic_computer_180dp_with_padding);
        }
    }

    public static void requestKillAllQemuProcess(Activity activity, Runnable runnable) {
        DialogUtils.twoDialog(activity, activity.getString(R.string.do_you_want_to_kill_all_qemu_processes), activity.getString(R.string.all_running_vms_will_be_forcibly_shut_down), activity.getString(R.string.kill_all), activity.getString(R.string.cancel), true, R.drawable.power_settings_new_24px, true,
                () -> {
                    killallqemuprocesses(activity);
                    if (runnable != null) runnable.run();
                }, null, null);
    }


    public static void killcurrentqemuprocess(Activity activity) {
        Terminal.requestStopStreaming();
        boolean stopped = stopVmProcess(activity, com.vectras.vm.main.core.MainStartVM.lastVMID, true);
        if (!stopped) {
            Terminal vterm = new Terminal(activity);
            String targetBinary;
            switch (MainSettingsManager.getArch(activity)) {
                case "ARM64":
                    targetBinary = "qemu-system-aarch64";
                    break;
                case "PPC":
                    targetBinary = "qemu-system-ppc";
                    break;
                case "I386":
                    targetBinary = "qemu-system-i386";
                    break;
                default:
                    targetBinary = "qemu-system-x86_64";
                    break;
            }
            long pid = ProcessRuntimeOps.safePid(Terminal.qemuProcess);
            if (pid > 0) {
                vterm.executeShellCommand2("kill -15 " + pid + " || pkill -15 -f " + targetBinary, false, null);
            } else {
                Log.w(TAG, "killcurrentqemuprocess: terminal PID unavailable, using binary fallback");
                vterm.executeShellCommand2("pkill -15 -f " + targetBinary, false, null);
            }
        }
    }

    private static boolean isPidAlive(long pid) {
        if (pid <= 0) {
            return false;
        }
        try {
            Process probe = new ProcessBuilder("kill", "-0", Long.toString(pid)).start();
            boolean finished = probe.waitFor(700, TimeUnit.MILLISECONDS);
            return !finished || probe.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean signalPidAndAwait(long pid, int signal, long timeoutMs) {
        if (pid <= 0) {
            return false;
        }
        if (signal <= 0 || signal > 64) {
            return false;
        }
        try {
            new ProcessBuilder("kill", "-" + signal, Long.toString(pid))
                    .start()
                    .waitFor(700, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            return false;
        }
        long deadline = ProcessRuntimeOps.monoMs() + Math.max(0, timeoutMs);
        while (ProcessRuntimeOps.monoMs() < deadline) {
            if (!isPidAlive(pid)) {
                return true;
            }
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !isPidAlive(pid);
    }

    private static synchronized void resetLifecycleStateAfterKillAll() {
        for (Map.Entry<String, ProcessSupervisor> entry : new HashMap<>(SUPERVISORS).entrySet()) {
            String vmId = entry.getKey();
            ProcessSupervisor supervisor = entry.getValue();
            if (supervisor == null) {
                continue;
            }

            long pid = supervisor.getPid();
            VM_STATES.put(vmId, VmRuntimeState.STOPPING);
            boolean stoppedGracefully = supervisor.stopGracefully(true);
            boolean terminated = !isPidAlive(pid);

            if (!terminated && pid > 0) {
                Log.w(TAG, "killall fallback TERM by pid vmId=" + vmId + " pid=" + pid + " graceful=" + stoppedGracefully);
                terminated = signalPidAndAwait(pid, 15, 2_500);
            }

            if (!terminated && pid > 0) {
                long terminalPid = ProcessRuntimeOps.safePid(Terminal.qemuProcess);
                if (terminalPid <= 0) {
                    Log.w(TAG, "killall fallback KILL: terminal PID unavailable for vmId=" + vmId);
                }
                if (terminalPid > 0 && terminalPid == pid) {
                    try {
                        Terminal.qemuProcess.destroyForcibly();
                    } catch (RuntimeException ignored) {
                    }
                }
                Log.w(TAG, "killall fallback KILL by pid vmId=" + vmId + " pid=" + pid);
                terminated = signalPidAndAwait(pid, 9, 1_500);
            }

            if (terminated) {
                SUPERVISORS.remove(vmId, supervisor);
                VM_STATES.put(vmId, VmRuntimeState.STOPPED);
                releaseSlotForKey(vmId, "killall_terminated");
            } else {
                VM_STATES.put(vmId, VmRuntimeState.RUNNING);
                Log.e(TAG, "killall could not terminate vmId=" + vmId + " pid=" + pid + " (state preserved, avoiding false STOPPED)");
            }
        }
    }

    public static ProcessBudgetRegistry.Snapshot getProcessBudgetSnapshot() {
        return ProcessBudgetRegistry.get().snapshot();
    }

    private static void releaseSlotForKey(String key, String reason) {
        ProcessBudgetRegistry.SlotToken token = SUPERVISOR_SLOTS.remove(key);
        if (token != null) {
            ProcessBudgetRegistry.get().releaseSlot(token, reason);
        }
    }

    public static synchronized void killallqemuprocesses(Context context) {
        Terminal.requestStopStreaming();
        resetLifecycleStateAfterKillAll();
        Terminal vterm = new Terminal(context);
        if (Terminal.qemuProcess != null) {
            long pid = ProcessRuntimeOps.safePid(Terminal.qemuProcess);
            if (pid <= 0) {
                Log.w(TAG, "killallqemuprocesses: terminal PID unavailable, skip direct signal path");
            }
            if (pid > 0 && isPidAlive(pid)) {
                vterm.executeShellCommand2("kill -15 " + pid, false, null);
                if (!signalPidAndAwait(pid, 15, 2_000)) {
                    try {
                        Terminal.qemuProcess.destroyForcibly();
                    } catch (RuntimeException ignored) {
                    }
                    if (!signalPidAndAwait(pid, 9, 1_500)) {
                        Log.e(TAG, "killall could not terminate terminal-owned pid=" + pid);
                    }
                }
            }
        }
        if (!MainStartVM.lastVMName.isEmpty()) {
            RafaeliaEventRecorder.recordStop(context, MainStartVM.lastVMName);
        }
        closeVmFdsOnStop();
    }

    public static void shutdownCurrentVM() {
        new Thread(() -> {
            QmpClient.sendCommand("{ \"execute\": \"quit\" }");
            closeVmFdsOnStop();
        }).start();
    }

    public static void resetCurrentVM() {
        new Thread(() -> QmpClient.sendCommand("{ \"execute\": \"system_reset\" }")).start();
    }

    public static void showChangeRemovableDevicesDialog(Activity _activity, VncCanvasActivity vncCanvasActivity) {
        new Thread(() -> {
            String allDevice = getAllDevicesInQemu();

            _activity.runOnUiThread(() -> {
                View _view = LayoutInflater.from(_activity).inflate(R.layout.dialog_change_removable_devices, null);
                AlertDialog _dialog = new MaterialAlertDialogBuilder(_activity, R.style.CenteredDialogTheme)
                        .setView(_view)
                        .create();

                if (allDevice != null && (allDevice.contains("ide1-cd0")
                        || allDevice.contains("ide2-cd0")
                        || allDevice.contains("floppy0")
                        || allDevice.contains("floppy1")
                        || allDevice.contains("sd0"))) {

                    if (allDevice.contains("ide1-cd0")
                            || allDevice.contains("ide2-cd0")) {

                        _view.findViewById(R.id.ln_cdrom).setOnClickListener(v -> {
                            Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            _activity.startActivityForResult(intent, 120);
                            _dialog.dismiss();
                        });

                        _view.findViewById(R.id.iv_ejectcdrom).setOnClickListener(v -> {
                            ejectCDROM(_activity);
                            _dialog.dismiss();
                        });
                    } else {
                        _view.findViewById(R.id.ln_cdrom).setVisibility(View.GONE);
                    }

                    if (allDevice.contains("floppy0")) {
                        _view.findViewById(R.id.ln_fda).setOnClickListener(v -> {
                            Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            _activity.startActivityForResult(intent, 889);
                            _dialog.dismiss();
                        });

                        _view.findViewById(R.id.iv_ejectfda).setOnClickListener(v -> {
                            ejectFloppyDriveA(_activity);
                            _dialog.dismiss();
                        });

                        if (!allDevice.contains("floppy1")) {
                            TextView tvFda = _view.findViewById(R.id.tv_fda);
                            tvFda.setText(R.string.floppy_drive);
                        }
                    } else {
                        _view.findViewById(R.id.ln_fda).setVisibility(View.GONE);
                    }

                    if (allDevice.contains("floppy1")) {
                        _view.findViewById(R.id.ln_fdb).setOnClickListener(v -> {
                            Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            _activity.startActivityForResult(intent, 13335);
                            _dialog.dismiss();
                        });

                        _view.findViewById(R.id.iv_ejectfdb).setOnClickListener(v -> {
                            ejectFloppyDriveB(_activity);
                            _dialog.dismiss();
                        });

                        if (!allDevice.contains("floppy0")) {
                            TextView tvFdb = _view.findViewById(R.id.tv_fdb);
                            tvFdb.setText(R.string.floppy_drive);
                        }
                    } else {
                        _view.findViewById(R.id.ln_fdb).setVisibility(View.GONE);
                    }

                    if (allDevice.contains("sd0")) {
                        _view.findViewById(R.id.ln_sd).setOnClickListener(v -> {
                            Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            _activity.startActivityForResult(intent, 32);
                            _dialog.dismiss();
                        });

                        _view.findViewById(R.id.iv_ejectsd).setOnClickListener(v -> {
                            ejectSDCard(_activity);
                            _dialog.dismiss();
                        });
                    } else {
                        _view.findViewById(R.id.ln_sd).setVisibility(View.GONE);
                    }

                    _view.findViewById(R.id.ln_otherdevice).setOnClickListener(v -> {
                        showChangeRemovableDevicesWithIDDialog(_activity);
                        _dialog.dismiss();
                    });
                } else {
                    TextView tvFdb = _view.findViewById(R.id.tv_otherdevice);
                    tvFdb.setText(R.string.change_or_eject_a_device);

                    _view.findViewById(R.id.ln_cdrom).setVisibility(View.GONE);
                    _view.findViewById(R.id.ln_fda).setVisibility(View.GONE);
                    _view.findViewById(R.id.ln_fdb).setVisibility(View.GONE);
                    _view.findViewById(R.id.ln_sd).setVisibility(View.GONE);
                }

                if (vncCanvasActivity != null) {
                    _view.findViewById(R.id.ln_refresh).setOnClickListener(v -> {
                        _activity.startActivity(new Intent(_activity, MainVNCActivity.class));
                        _activity.overridePendingTransition(0, 0);
                        _activity.finish();
                        _dialog.dismiss();
                    });

                    if (ConnectionBean.useLocalCursor) {
                        TextView tvvirtualmouse = _view.findViewById(R.id.tv_virtualmouse);
                        tvvirtualmouse.setText(_activity.getString(R.string.hide_virtual_mouse));
                    }

                    _view.findViewById(R.id.ln_virtualmouse).setOnClickListener(v -> {
                        MainSettingsManager.setShowVirtualMouse(_activity, !ConnectionBean.useLocalCursor);
                        ConnectionBean.useLocalCursor = !ConnectionBean.useLocalCursor;
                        _dialog.dismiss();
                    });

                    _view.findViewById(R.id.ln_mouse).setOnClickListener(v -> {
                        MainVNCActivity.getContext.onMouseMode();
                        _dialog.dismiss();
                    });

                    _view.findViewById(R.id.ln_settings).setOnClickListener(v -> {
                        _activity.startActivity(new Intent(_activity, VNCSettingsActivity.class));
                        _dialog.dismiss();
                    });

                    if (MainSettingsManager.getVNCScaleMode(_activity) == VNCConfig.oneToOne) {
                        _view.findViewById(R.id.iv_screenOneToOne).setBackgroundResource(R.drawable.dialog_shape_single_button);
                    } else {
                        _view.findViewById(R.id.iv_screenFit).setBackgroundResource(R.drawable.dialog_shape_single_button);
                    }

                    _view.findViewById(R.id.iv_screenOneToOne).setOnClickListener(v -> {
                        MainSettingsManager.setVNCScaleMode(_activity, VNCConfig.oneToOne);
                        _activity.startActivity(new Intent(_activity, MainVNCActivity.class));
                        _activity.overridePendingTransition(0, 0);
                        _activity.finish();
                        _dialog.dismiss();
                    });

                    _view.findViewById(R.id.iv_screenFit).setOnClickListener(v -> {
                        MainSettingsManager.setVNCScaleMode(_activity, VNCConfig.fitToScreen);
                        _activity.startActivity(new Intent(_activity, MainVNCActivity.class));
                        _activity.overridePendingTransition(0, 0);
                        _activity.finish();
                        _dialog.dismiss();
                    });
                } else {
                    _view.findViewById(R.id.ln_user_interface).setVisibility(View.GONE);
                }

                if (!DialogUtils.isAllowShow(_activity)) return;
                _dialog.show();
            });
        }).start();
    }

    public static void showChangeRemovableDevicesWithIDDialog(Activity _activity) {
        View _view = LayoutInflater.from(_activity).inflate(R.layout.widget_edittext_dialog, null);
        AlertDialog _dialog = new MaterialAlertDialogBuilder(_activity, R.style.CenteredDialogTheme)
                .setTitle(_activity.getString(R.string.change_a_removable_device))
                .setView(_view)
                .create();

        EditText _edittext = _view.findViewById(R.id.editText);
        TextInputLayout _textInputLayout = _view.findViewById(R.id.textInputLayout);
        _textInputLayout.setHint(_activity.getString(R.string.enter_device_id));

        _dialog.setButton(DialogInterface.BUTTON_POSITIVE, _activity.getString(R.string.change_disk_file), (dialog, which) -> {
            if (!_edittext.getText().toString().isEmpty()) {
                pendingDeviceID = _edittext.getText().toString();

                Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                _activity.startActivityForResult(intent, 1996);
                _dialog.dismiss();
            } else {
                Toast.makeText(_activity, _activity.getString(R.string.you_need_to_enter_the_device_id), Toast.LENGTH_SHORT).show();
            }
        });

        _dialog.setButton(DialogInterface.BUTTON_NEUTRAL, _activity.getString(R.string.eject), (dialog, which) -> {
            if (!_edittext.getText().toString().isEmpty()) {
                ejectRemovableDevice(_edittext.getText().toString(), _activity);
                _dialog.dismiss();
            } else {
                Toast.makeText(_activity, _activity.getString(R.string.you_need_to_enter_the_device_id), Toast.LENGTH_SHORT).show();
            }
        });

        _dialog.setButton(DialogInterface.BUTTON_NEGATIVE, _activity.getString(R.string.close), (dialog, which) -> _dialog.dismiss());

        _dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            _edittext.requestFocus();
            _edittext.setSelection(_edittext.getText().length());
            InputMethodManager imm = (InputMethodManager) _activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(_edittext, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    public static void changeCDROM(String _path, Activity _activity) {
        new Thread(() -> {
            if (isUsingQ35(lastQemuCommand)) {
                if (isQMPCommandSuccess(QmpClient.sendCommand(changeRemovableDevicesQMPCommand("ide2-cd0", _path)))) {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
                } else {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
                }
            } else {
                if (isQMPCommandSuccess(QmpClient.sendCommand(changeRemovableDevicesQMPCommand("ide1-cd0", _path)))) {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
                } else {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    public static void changeFloppyDriveA(String _path, Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(changeRemovableDevicesQMPCommand("floppy0", _path)))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void changeFloppyDriveB(String _path, Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(changeRemovableDevicesQMPCommand("floppy1", _path)))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void changeSDCard(String _path, Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(changeRemovableDevicesQMPCommand("sd0", _path)))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void ejectCDROM(Activity _activity) {
        new Thread(() -> {
            if (isUsingQ35(lastQemuCommand)) {
                if (isQMPCommandSuccess(QmpClient.sendCommand(ejectRemovableDevicesQMPCommand("ide2-cd0")))) {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
                } else {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
                }
            } else {
                if (isQMPCommandSuccess(QmpClient.sendCommand(ejectRemovableDevicesQMPCommand("ide1-cd0")))) {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
                } else {
                    if (_activity != null && !_activity.isFinishing())
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    public static void ejectFloppyDriveA(Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(ejectRemovableDevicesQMPCommand("floppy0")))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void ejectFloppyDriveB(Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(ejectRemovableDevicesQMPCommand("floppy1")))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void ejectSDCard(Activity _activity) {
        new Thread(() -> {
            if (isQMPCommandSuccess(QmpClient.sendCommand(ejectRemovableDevicesQMPCommand("sd0")))) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static void changeRemovableDevice(String _deviceID, String _filepath, Activity _activity) {
        new Thread(() -> {
            String _result = QmpClient.sendCommand(changeRemovableDevicesQMPCommand(_deviceID, _filepath));
            if (isQMPCommandSuccess(_result)) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.changed), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing()) {
                    if (_result.contains("is not removable")) {
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.this_is_not_a_removable_device), Toast.LENGTH_SHORT).show());
                    } else {
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.change_failed), Toast.LENGTH_SHORT).show());
                    }
                }
            }
        }).start();
    }

    public static void ejectRemovableDevice(String _deviceID, Activity _activity) {
        new Thread(() -> {
            String _result = QmpClient.sendCommand(ejectRemovableDevicesQMPCommand(_deviceID));
            if (isQMPCommandSuccess(_result)) {
                if (_activity != null && !_activity.isFinishing())
                    _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.ejected), Toast.LENGTH_SHORT).show());
            } else {
                if (_activity != null && !_activity.isFinishing()) {
                    if (_result.contains("is not removable")) {
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.this_is_not_a_removable_device), Toast.LENGTH_SHORT).show());
                    } else {
                        _activity.runOnUiThread(() -> Toast.makeText(_activity, _activity.getString(R.string.eject_failed), Toast.LENGTH_SHORT).show());
                    }
                }
            }
        }).start();
    }

    public static void pressPowerButton() {
        new Thread(() -> QmpClient.sendCommand("{ \"execute\": \"system_powerdown\" }")).start();
    }

    public static void sendLeftMouseKey() {
        pressAKey("left");
    }

    public static void sendRightMouseKey() {
        pressAKey("right");
    }

    public static void sendMiddleMouseKey() {
        pressAKey("middle");
    }

    public static void sendSuperKey() {
        keyDown("KEY_LEFTMETA");
    }

    public static void sendHoldSuperKey() {
        keyDown("KEY_LEFTMETA");
    }

    public static void sendReleaseSuperKey() {
        keyUp("KEY_LEFTMETA");
    }

    public static void pressAKey(String key) {
        new Thread(() -> {
            try {
                keyDown(key);
                Thread.sleep(50);
                keyUp(key);
            } catch (InterruptedException e) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "pressAKey: " + e.getMessage());
                }
            }
        }).start();
    }

    public static void keyDown(String key) {
        QmpClient.sendCommand(sendKeyCommand(key, true));
    }

    public static void keyUp(String key) {
        QmpClient.sendCommand(sendKeyCommand(key, false));
    }

    public static String sendKeyCommand(String key, Boolean isDown) {
        return "{" +
                "  \"execute\": \"input-send-event\"," +
                "  \"arguments\": {" +
                "    \"events\": [" +
                "      {" +
                "        \"type\": \"btn\"," +
                "        \"data\": {" +
                "          \"button\": \"" + key + "\"," +
                "          \"down\": " + (isDown ? "true" : "false") +
                "        }" +
                "      }" +
                "    ]" +
                "  }" +
                "}";
    }

    public static void setVNCPasswordWithDelay(String _password) {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                setVNCPassword(_password);
            } catch (InterruptedException e) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "setVNCPasswordWithDelay: " + e.getMessage());
                }
            }
        }).start();
    }

    public static void setVNCPassword(String _password) {
        String _result = QmpClient.sendCommand(changeVNCPasswordQMPCommand(_password));
        if (isQMPCommandSuccess(_result)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "setVNCPassword: Success");
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "setVNCPassword: Failed");
            }
        }
    }

    @NonNull
    @Contract(pure = true)
    public static String changeRemovableDevicesQMPCommand(String _device, String _filepath) {
        return "{ \n" +
                "  \"execute\": \"blockdev-change-medium\", \n" +
                "  \"arguments\": { \n" +
                "    \"device\": \"" + _device + "\", \n" +
                "    \"filename\": \"" + _filepath + "\", \n" +
                "    \"format\": \"raw\" \n" +
                "  } \n" +
                "}";
    }


    @NonNull
    @Contract(pure = true)
    public static String ejectRemovableDevicesQMPCommand(String _device) {
        return "{ \"execute\": \"eject\", \"arguments\": { \"device\": \"" + _device + "\" } }";
    }

    public static String getAllDevicesInQemu() {
        return QmpClient.sendCommand("{ \"execute\": \"query-block\" }");
    }

    public static String changeVNCPasswordQMPCommand(String _password) {
        return "{ \"execute\": \"change-vnc-password\", \"arguments\": { \"password\": \"" + _password + "\" } }";
    }

    public static boolean isQMPCommandSuccess(String _result) {
        if (_result == null) return false;

        if (BuildConfig.DEBUG) {
            Log.d("VMManager", "isQMPCommandSuccess: " + _result);
        }
        return _result.contains("\"return\": {}");
    }


    @Contract(pure = true)
    public static boolean isUsingQemuARM(@NonNull String _qemuCommand) {
        return _qemuCommand.contains("qemu-system-a");
    }

    @Contract(pure = true)
    public static boolean isUsingQemuPowerPC(@NonNull String _qemuCommand) {
        return _qemuCommand.contains("qemu-system-p");
    }

    public static boolean isUsingQ35(@NonNull String _qemuCommand) {
        return _qemuCommand.contains("-M q35")
                || _qemuCommand.contains("-machine q35")
                || _qemuCommand.contains("-M pc-q35")
                || _qemuCommand.contains("-machine pc-q35");
    }

    public static boolean isNeedUseVirtualMouse() {
        return lastQemuCommand.contains("-vga qxl") ||
                lastQemuCommand.contains("-vga virtio") ||
                lastQemuCommand.contains("-device qxl-vga") ||
                lastQemuCommand.contains("-device virtio-vga") ||
                lastQemuCommand.contains("-device virtio-gpu");
    }

    public static String addAudioDevSdl(String env) {
        final String audioDevParam = ",audiodev=defaultaudiodev -audiodev sdl,id=defaultaudiodev ";
        String result = env;
        if (env.startsWith("-device hda-duplex ") || env.contains(" -device hda-duplex ") || env.endsWith(" -device hda-duplex")) {
            result = result.replaceFirst(" -device hda-duplex", " -device hda-duplex" + audioDevParam);
        } else if (env.startsWith("-device cs4231a ") || env.contains(" -device cs4231a ") || env.endsWith(" -device cs4231a")) {
            result = result.replaceFirst(" -device cs4231a", " -device cs4231a" + audioDevParam);
        } else if (env.startsWith("-device ac97 ") || env.contains(" -device ac97 ") || env.endsWith(" -device ac97")) {
            result = result.replaceFirst(" -device ac97", " -device ac97" + audioDevParam);
        } else if (env.startsWith("-device es1370 ") || env.contains(" -device es1370 ") || env.endsWith(" -device es1370")) {
            result = result.replaceFirst(" -device es1370", " -device es1370" + audioDevParam);
        } else if (env.startsWith("-device sb16 ") || env.contains(" -device sb16 ") || env.endsWith(" -device sb16")) {
            result = result.replaceFirst(" -device sb16", " -device sb16" + audioDevParam);
        } else if (env.startsWith("-device adlib ") || env.contains(" -device adlib ") || env.endsWith(" -device adlib")) {
            result = result.replaceFirst(" -device adlib", " -device adlib" + audioDevParam);
        }
        return result;
    }
}
