package com.vectras.vm.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.documentfile.provider.DocumentFile;

import com.vectras.vm.R;

public class PermissionUtils {
    public static final int REQUEST_LEGACY_STORAGE = 1000;

    public static final int REQUEST_CODE_OVERLAY_SETTINGS = 1001;
    public static final int REQUEST_CODE_BATTERY_OPTIMIZATION_SETTINGS = 1002;

    public interface OnDocumentTreePermissionResult {
        void onResult(@Nullable Uri uri);
    }

    public static boolean storagepermission(Activity activity, boolean request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses scoped storage by default.
            return true;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        if (request) {
            requestLegacyStoragePermission(activity);
        }
        return false;
    }

    public static void requestStoragePermission(Activity activity, ActivityResultLauncher<Uri> openDocumentTreeLauncher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDocumentTreeLauncher.launch(null);
            return;
        }
        requestLegacyStoragePermission(activity);
    }


    public static boolean isAllFilesAccessGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        return Environment.isExternalStorageManager();
    }

    public static void openAllFilesAccessSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyStoragePermission(activity);
            return;
        }

        Intent appSettingsIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        appSettingsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (appSettingsIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(appSettingsIntent);
            return;
        }

        Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        if (fallbackIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(fallbackIntent);
            return;
        }

        Intent detailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        detailsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(detailsIntent);
    }
    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Toast.makeText(activity, R.string.storage_permission_explanation_android11, Toast.LENGTH_LONG).show();
            return;
        }
        requestLegacyStoragePermission(activity);
    }

    /**
     * Runtime storage permission matrix:
     * API <= 28  -> request WRITE_EXTERNAL_STORAGE.
     * API 29-32  -> no runtime storage permission request (Scoped Storage / SAF path).
     * API >= 33  -> no WRITE/READ_EXTERNAL runtime request here; use SAF or READ_MEDIA_* flows as needed.
     */
    private static void requestLegacyStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return;
        }

        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            Toast.makeText(activity, activity.getResources().getString(R.string.find_and_allow_access_to_storage_in_settings), Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_LEGACY_STORAGE);
        }
    }

    public static ActivityResultLauncher<Uri> registerOpenDocumentTreeLauncher(FragmentActivity activity,
                                                                                OnDocumentTreePermissionResult callback) {
        return activity.registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri == null) {
                callback.onResult(null);
                return;
            }

            if (persistTreePermission(activity, uri)) {
                callback.onResult(uri);
            } else {
                callback.onResult(null);
            }
        });
    }

    public static boolean persistTreePermission(Activity activity, Uri uri) {
        if (uri == null) return false;
        try {
            activity.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return true;
        } catch (SecurityException ignored) {
            // non-fatal fallback
            return false;
        }
    }

    public static boolean hasPersistedTreePermission(Activity activity) {
        for (UriPermission persistedPermission : activity.getContentResolver().getPersistedUriPermissions()) {
            if (persistedPermission != null && persistedPermission.isReadPermission()) {
                return true;
            }
        }
        return false;
    }

    public static DocumentFile resolveTree(Activity activity, Uri uri) {
        if (uri == null) return null;
        return DocumentFile.fromTreeUri(activity, uri);
    }

    /**
     * Special access matrix (Settings-based, no requestPermissions):
     * Overlay (SYSTEM_ALERT_WINDOW): API >= 23 via ACTION_MANAGE_OVERLAY_PERMISSION + post-return check with canDrawOverlays().
     * Battery optimization whitelist (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS): API >= 23 via ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS +
     * post-return check with isIgnoringBatteryOptimizations().
     */
    public static boolean canDrawOverlays(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(activity);
    }

    public static void openOverlayPermissionSettings(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || canDrawOverlays(activity)) {
            return;
        }

        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
        }
        activity.startActivityForResult(intent, requestCode);
    }

    public static boolean isIgnoringBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager pm = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    public static void openBatteryOptimizationSettings(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations(activity)) {
            return;
        }

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        }
        activity.startActivityForResult(intent, requestCode);
    }

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

        DialogUtils.twoDialog(
                activity,
                activity.getString(R.string.storage_permission_title),
                message,
                activity.getString(R.string.proceed),
                activity.getString(R.string.cancel),
                true,
                R.drawable.folder_24px,
                true,
                onProceed,
                null,
                null
        );
    }
}
