package com.vectras.vm.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DownloadStateReconciler {

    private static final String TAG = "DownloadReconciler";

    private DownloadStateReconciler() {
    }

    public static void reconcileOnAppStart(@NonNull Context context) {
        Thread thread = new Thread(() -> reconcileBlocking(context.getApplicationContext()), "download-state-reconcile");
        thread.setDaemon(true);
        thread.start();
    }

    private static void reconcileBlocking(@NonNull Context context) {
        DownloadStateStore store = new DownloadStateStore(context);
        WorkManager workManager = WorkManager.getInstance(context);

        try {
            List<WorkInfo> workInfos = workManager.getWorkInfosByTag(DownloadCoordinator.DOWNLOAD_WORK_TAG).get();
            Map<String, WorkInfo> byRomId = new HashMap<>();
            for (WorkInfo info : workInfos) {
                String romId = null;
                for (String tag : info.getTags()) {
                    if (tag != null && tag.startsWith("rom_download_tag_")) {
                        romId = tag.substring("rom_download_tag_".length());
                        break;
                    }
                }
                if (romId != null && !romId.trim().isEmpty()) {
                    byRomId.put(romId, info);
                }
            }

            for (DownloadItemState itemState : store.getAll()) {
                WorkInfo workInfo = byRomId.get(itemState.id);
                if (workInfo == null) {
                    if (DownloadStatus.RUNNING.equals(itemState.status) || DownloadStatus.QUEUED.equals(itemState.status)) {
                        store.updateStatus(itemState.id, DownloadStatus.FAILED);
                    }
                    continue;
                }

                store.updateStatus(itemState.id, mapStatus(workInfo.getState()));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to reconcile download states", e);
        }
    }

    @NonNull
    private static String mapStatus(@NonNull WorkInfo.State state) {
        if (state == WorkInfo.State.ENQUEUED) {
            return DownloadStatus.QUEUED;
        }
        if (state == WorkInfo.State.RUNNING) {
            return DownloadStatus.RUNNING;
        }
        if (state == WorkInfo.State.BLOCKED) {
            return DownloadStatus.PAUSED;
        }
        if (state == WorkInfo.State.SUCCEEDED) {
            return DownloadStatus.COMPLETED;
        }
        if (state == WorkInfo.State.CANCELLED) {
            return DownloadStatus.CANCELED;
        }
        return DownloadStatus.FAILED;
    }
}
