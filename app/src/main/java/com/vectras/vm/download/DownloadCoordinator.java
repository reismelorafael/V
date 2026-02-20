package com.vectras.vm.download;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DownloadCoordinator {

    private final WorkManager workManager;

    public DownloadCoordinator(@NonNull Context context) {
        workManager = WorkManager.getInstance(context.getApplicationContext());
    }

    @NonNull
    public UUID enqueueRomDownload(@NonNull String romId,
                                   @NonNull String url,
                                   @NonNull String finalName,
                                   @Nullable String expectedHash) {
        Data input = new Data.Builder()
                .putString(DownloadWorker.KEY_ROM_ID, romId)
                .putString(DownloadWorker.KEY_URL, url)
                .putString(DownloadWorker.KEY_FINAL_NAME, finalName)
                .putString(DownloadWorker.KEY_EXPECTED_HASH, expectedHash)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(tagForRom(romId))
                .build();

        String uniqueWorkName = DownloadWorker.WORK_NAME_PREFIX + romId;
        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, request);
        return request.getId();
    }

    public void cancelRomDownload(@NonNull String romId) {
        workManager.cancelUniqueWork(DownloadWorker.WORK_NAME_PREFIX + romId);
    }

    public void cancelById(@NonNull UUID requestId) {
        workManager.cancelWorkById(requestId);
    }

    @NonNull
    public String tagForRom(@NonNull String romId) {
        return "rom_download_tag_" + romId;
    }

    @NonNull
    public androidx.lifecycle.LiveData<WorkInfo> observeDownload(@NonNull UUID requestId) {
        return workManager.getWorkInfoByIdLiveData(requestId);
    }
}
