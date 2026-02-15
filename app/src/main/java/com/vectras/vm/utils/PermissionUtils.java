package com.vectras.vm.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

    public static void requestStoragePermission(Activity activity, ActivityResultLauncher<Uri> openDocumentTreeLauncher) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDocumentTreeLauncher.launch(null);
            return;
        }
        requestLegacyStoragePermission(activity);
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
