package com.vectras.vm.download;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DownloadViewModel extends AndroidViewModel {

    private final DownloadCoordinator coordinator;
    private final DownloadStateStore stateStore;
    private final WorkManager workManager;
    private final Map<String, MediatorLiveData<DownloadItemState>> stateByRomId = new HashMap<>();

    public DownloadViewModel(@NonNull Application application) {
        super(application);
        coordinator = new DownloadCoordinator(application);
        stateStore = new DownloadStateStore(application);
        workManager = WorkManager.getInstance(application);
    }

    @NonNull
    public LiveData<DownloadItemState> observeState(@NonNull String romId) {
        MediatorLiveData<DownloadItemState> existing = stateByRomId.get(romId);
        if (existing != null) {
            return existing;
        }

        MediatorLiveData<DownloadItemState> liveData = new MediatorLiveData<>();
        liveData.setValue(stateStore.getById(romId));
        LiveData<List<WorkInfo>> workStateLiveData = workManager.getWorkInfosByTagLiveData(coordinator.tagForRom(romId));
        liveData.addSource(workStateLiveData, workInfos -> {
            reconcileStateFromWorkInfo(romId, workInfos);
            liveData.setValue(stateStore.getById(romId));
        });
        stateByRomId.put(romId, liveData);
        return liveData;
    }

    public void enqueueOrResume(@NonNull String romId,
                                @NonNull String url,
                                @NonNull String finalName,
                                @Nullable String expectedHash) {
        coordinator.enqueueRomDownload(romId, url, finalName, expectedHash);
        publish(romId);
    }

    public void pause(@NonNull String romId) {
        coordinator.pauseRomDownload(romId);
        publish(romId);
    }

    public void cancel(@NonNull String romId) {
        coordinator.cancelRomDownload(romId);
        publish(romId);
    }

    @Nullable
    public DownloadItemState getCurrent(@NonNull String romId) {
        return stateStore.getById(romId);
    }

    private void publish(@NonNull String romId) {
        MutableLiveData<DownloadItemState> liveData = stateByRomId.get(romId);
        if (liveData != null) {
            liveData.postValue(stateStore.getById(romId));
        }
    }

    private void reconcileStateFromWorkInfo(@NonNull String romId, @Nullable List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }

        WorkInfo latest = infos.get(0);
        for (WorkInfo info : infos) {
            if (info.getRunAttemptCount() >= latest.getRunAttemptCount()) {
                latest = info;
            }
        }

        if (latest.getState() == WorkInfo.State.ENQUEUED) {
            stateStore.updateStatus(romId, DownloadStatus.QUEUED);
        } else if (latest.getState() == WorkInfo.State.RUNNING) {
            stateStore.updateStatus(romId, DownloadStatus.RUNNING);
        } else if (latest.getState() == WorkInfo.State.CANCELLED) {
            DownloadItemState current = stateStore.getById(romId);
            if (current != null && DownloadStatus.RUNNING.equals(current.status)) {
                stateStore.updateStatus(romId, DownloadStatus.CANCELED);
            }
        }
    }
}
