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

    public static boolean hasStorageCapability(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return hasPersistedTreePermission(activity);
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasNotificationCapability(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasMediaReadCapability(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isBatteryOptimizationIgnored(Activity activity) {
        PowerManager powerManager = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    public static boolean hasOverlayCapability(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(activity);
    }

    public static Intent buildBatteryOptimizationSettingsIntent(Activity activity) {
        Intent ignoreIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        ignoreIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (ignoreIntent.resolveActivity(activity.getPackageManager()) != null) {
            return ignoreIntent;
        }

        Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
            return settingsIntent;
        }

        Intent detailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        detailsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        return detailsIntent;
    }

    public static Intent buildOverlaySettingsIntent(Activity activity) {
        Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        overlayIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (overlayIntent.resolveActivity(activity.getPackageManager()) != null) {
            return overlayIntent;
        }

        Intent detailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        detailsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        return detailsIntent;
    }

    public static void requestStoragePermission(Activity activity, ActivityResultLauncher<Uri> openDocumentTreeLauncher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDocumentTreeLauncher.launch(null);
            return;
        }
        requestLegacyStoragePermission(activity);
    }

    public static boolean hasNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(activity, PermissionCapabilitySupport.getNotificationPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestNotificationPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        ActivityCompat.requestPermissions(
                activity,
                new String[]{PermissionCapabilitySupport.getNotificationPermission()},
                requestCode
        );
    }

    public static boolean hasMediaReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (String permission : PermissionCapabilitySupport.getMediaReadPermissions()) {
                if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
                    return true;
                }
            }
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }

        return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestMediaReadPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, PermissionCapabilitySupport.getMediaReadPermissions(), requestCode);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return;
        }

        requestLegacyStoragePermission(activity);
    }

    public static boolean isIgnoringBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        PowerManager powerManager = activity.getSystemService(PowerManager.class);
        if (powerManager == null) {
            return false;
        }
        return powerManager.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    public static void openBatteryOptimizationSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Intent ignoreIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        ignoreIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (ignoreIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(ignoreIntent);
            return;
        }

        Intent settingsIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(settingsIntent);
        }
    }

    public static boolean canDrawOverlays(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(activity);
    }

    public static void openOverlaySettings(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        overlayIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        if (overlayIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(overlayIntent);
            return;
        }

        Intent detailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        detailsIntent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(detailsIntent);
    }

    public static boolean hasSafTreePermission(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }
        return hasPersistedTreePermission(activity);
    }

    public static void requestSafTreePermission(Activity activity, ActivityResultLauncher<Uri> openDocumentTreeLauncher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        openDocumentTreeLauncher.launch(null);
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

    private static void requestLegacyStoragePermission(Activity activity) {
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
