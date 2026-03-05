package com.vectras.vm.utils;

import android.Manifest;
import android.os.Build;

public final class PermissionCapabilitySupport {

    private PermissionCapabilitySupport() {
    }

    public static String getNotificationPermission() {
        return Manifest.permission.POST_NOTIFICATIONS;
    }

    public static String[] getMediaReadPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        }
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }
}
