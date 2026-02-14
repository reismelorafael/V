package com.vectras.vm;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Objects;

import com.vectras.vm.utils.UIUtils;
import com.vectras.vm.utils.PermissionUtils;

public class RomReceiverActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PermissionUtils.storagepermission(this, false)) {
            UIUtils.edgeToEdge(this);
            setContentView(R.layout.activity_cqcm);
            UIUtils.setOnApplyWindowInsetsListener(findViewById(R.id.main));
            Button buttonallow;
            buttonallow = findViewById(R.id.buttonallow);
            buttonallow.setOnClickListener(v -> {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.find_and_allow_access_to_storage_in_settings), Toast.LENGTH_LONG).show();
                } else {
                    ActivityCompat.requestPermissions(RomReceiverActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PermissionUtils.storagepermission(this, false)) {
            String filesDir = getFilesDir().getAbsolutePath();
            if ((new File(filesDir, "/distro/usr/local/bin/qemu-system-x86_64").exists()) || (new File(filesDir, "/distro/usr/bin/qemu-system-x86_64").exists())) {
                handleIncomingIntent(getIntent());
            } else {
                Toast.makeText(RomReceiverActivity.this, getResources().getString(R.string.you_need_to_complete_vectras_vm_setup_before_importing_this_file), Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, SplashActivity.class));
            }
            finish();
        }
    }

    private void handleIncomingIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (uri == null) {
            return;
        }

        String resolvedPath = getFilePath(uri);
        if (!isSupportedCvbiUri(uri, resolvedPath)) {
            Toast.makeText(this, getResources().getString(R.string.format_not_supported_please_select_file_with_format_cvbi), Toast.LENGTH_LONG).show();
            return;
        }

        Intent vmCreatorIntent = new Intent(this, VMCreatorActivity.class);
        vmCreatorIntent.putExtra("addromnow", "");
        vmCreatorIntent.putExtra("romextra", "");
        vmCreatorIntent.putExtra("romname", "");
        vmCreatorIntent.putExtra("romicon", "");
        vmCreatorIntent.putExtra("romfilename", ".cvbi");
        vmCreatorIntent.putExtra("rompath", resolvedPath);
        vmCreatorIntent.putExtra("romuri", uri.toString());
        startActivity(vmCreatorIntent);
        Log.i("ReceiveRomFileActivity", uri.toString());
        Log.i("ReceiveRomFileActivity", resolvedPath);
    }

    private boolean isSupportedCvbiUri(Uri uri, String resolvedPath) {
        String path = uri.getPath();
        if (!TextUtils.isEmpty(path) && path.toLowerCase().endsWith(".cvbi")) {
            return true;
        }

        if (!TextUtils.isEmpty(resolvedPath) && resolvedPath.toLowerCase().endsWith(".cvbi")) {
            return true;
        }

        String lastSegment = uri.getLastPathSegment();
        return !TextUtils.isEmpty(lastSegment) && lastSegment.toLowerCase().endsWith(".cvbi");
    }

    private String getFilePath(Uri _uri) {
        String result = "";
        if (_uri.toString().contains("/file%253A%252F%252F%252F")) {
            //Decrypt 2 times by FileProvider
            try {
                String decoded1 = URLDecoder.decode(_uri.getPath(), "UTF-8");
                String decoded2 = URLDecoder.decode(decoded1, "UTF-8");
                //No need to check and works perfectly with return decoded2.replace("file://", "");
                if (decoded2.startsWith("/file://")) {
                    result = decoded2.replace("/file://", "");
                } else {
                    result = decoded2.replace("file://", "");
                }
            } catch (UnsupportedEncodingException _e) {
                _e.printStackTrace();
            }
        } else {
            result = _uri.getPath();
        }

        if (result == null) {
            return "";
        }
        if (result.startsWith("/external_files")) {
            result = result.replace("/external_files", Environment.getExternalStorageDirectory().getAbsolutePath());
        }

        return result;
    }
}
