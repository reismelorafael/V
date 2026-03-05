package com.vectras.vm.setupwizard;

import android.app.Activity;
import android.os.Build;

import androidx.annotation.NonNull;

import com.vectras.vm.R;
import com.vectras.vm.utils.PermissionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FirstRunPermissionOrchestrator {
    public enum PermissionStatus {
        PENDING,
        GRANTED,
        SKIPPED,
        FAILED
    }

    public static class PermissionUiModel {
        public final String capability;
        public final String title;
        public final String description;
        public final boolean essential;
        public final boolean canOpenSettings;
        public final boolean canRetry;
        public PermissionStatus status;

        PermissionUiModel(String capability,
                          String title,
                          String description,
                          boolean essential,
                          boolean canOpenSettings,
                          boolean canRetry,
                          PermissionStatus status) {
            this.capability = capability;
            this.title = title;
            this.description = description;
            this.essential = essential;
            this.canOpenSettings = canOpenSettings;
            this.canRetry = canRetry;
            this.status = status;
        }
    }

    public static final String CAPABILITY_STORAGE = "storage";

    private final Activity activity;
    private final List<PermissionUiModel> models;

    public FirstRunPermissionOrchestrator(@NonNull Activity activity) {
        this.activity = activity;
        this.models = new ArrayList<>();
        models.add(new PermissionUiModel(
                CAPABILITY_STORAGE,
                activity.getString(R.string.allow_access_to_storage),
                activity.getString(R.string.you_need_to_allow_access_to_the_storage_to_continue),
                true,
                true,
                true,
                PermissionStatus.PENDING
        ));
        refresh();
    }

    public void refresh() {
        for (PermissionUiModel model : models) {
            if (CAPABILITY_STORAGE.equals(model.capability)) {
                boolean granted = isStorageGranted();
                if (granted) {
                    model.status = PermissionStatus.GRANTED;
                } else if (canSkipStorageByBusinessRule()) {
                    model.status = PermissionStatus.SKIPPED;
                } else if (model.status != PermissionStatus.FAILED) {
                    model.status = PermissionStatus.PENDING;
                }
            }
        }
    }

    public void markFailed(@NonNull String capability) {
        for (PermissionUiModel model : models) {
            if (capability.equals(model.capability)) {
                model.status = PermissionStatus.FAILED;
                return;
            }
        }
    }

    public boolean isEssentialResolved() {
        refresh();
        for (PermissionUiModel model : models) {
            if (!model.essential) {
                continue;
            }
            if (model.status != PermissionStatus.GRANTED && model.status != PermissionStatus.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    public List<PermissionUiModel> getUiModel() {
        refresh();
        return Collections.unmodifiableList(models);
    }

    private boolean isStorageGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return PermissionUtils.isAllFilesAccessGranted() || PermissionUtils.hasPersistedTreePermission(activity);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return PermissionUtils.hasPersistedTreePermission(activity);
        }
        return PermissionUtils.storagepermission(activity, false);
    }

    private boolean canSkipStorageByBusinessRule() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
    }
}
