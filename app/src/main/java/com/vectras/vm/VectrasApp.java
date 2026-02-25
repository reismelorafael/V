package com.vectras.vm;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.DynamicColors;
import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.core.DeterministicRuntimeMatrix;
import com.vectras.vm.download.DownloadStateReconciler;
import com.vectras.vm.utils.PackageUtils;
import com.vectras.vm.utils.UIUtils;
import com.vectras.vm.vectra.VectraCore;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class VectrasApp extends Application {
	public static VectrasApp vectrasapp;
	private static WeakReference<Context> context;

	public static Context getContext() {
		return context.get();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		vectrasapp = this;
		context = new WeakReference<>(getApplicationContext());

		Thread.setDefaultUncaughtExceptionHandler(
				new com.vectras.vm.crashtracker.CrashHandler(this)
		);

		try {
			Class.forName("android.os.AsyncTask");
		} catch (Throwable ignore) {
			// ignored
		}
        setupTheme();

		Locale locale = Locale.getDefault();
		String language = locale.getLanguage();

//		if (language.contains("ar")) {
//			overrideFont("DEFAULT", R.font.cairo_regular);
//		} else {
//			overrideFont("DEFAULT", R.font.gilroy);
//		}
		setupAppConfig(getApplicationContext());
		DownloadStateReconciler.reconcileOnAppStart(getApplicationContext());
		DeterministicRuntimeMatrix.Snapshot runtimeSnapshot = DeterministicRuntimeMatrix.capture();
		android.util.Log.i("VectraRuntime", "arch=" + runtimeSnapshot.arch + " cores=" + runtimeSnapshot.cores + " ptr=" + runtimeSnapshot.pointerBits + " page=" + runtimeSnapshot.pageBytes + " line=" + runtimeSnapshot.cacheLineBytes + " feat=" + runtimeSnapshot.features + " ioq=" + runtimeSnapshot.ioQuantumBytes + " irqUs=" + runtimeSnapshot.irqPeriodMicros + " workers=" + runtimeSnapshot.workerParallelism + " det=" + runtimeSnapshot.deterministicProduct);
		VectraCore.init(this, null);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (MainSettingsManager.getDynamicColor(activity))
                    DynamicColors.applyToActivityIfAvailable(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });

	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		// Cleanup VectraCore resources
		VectraCore.shutdown();
	}

	private void setupTheme() {
        UIUtils.setDarkOrLight(MainSettingsManager.getTheme(this));

//        if (MainSettingsManager.getDynamicColor(this))
//            DynamicColors.applyToActivitiesIfAvailable(this);

//        setTheme(R.style.AppTheme);
	}

	public static Context getApp() {
		return vectrasapp;
	}

	private void setupAppConfig(Context _context) {
		AppConfig.vectrasVersion = PackageUtils.getThisVersionName(_context);
		AppConfig.vectrasVersionCode = PackageUtils.getThisVersionCode(_context);
		AppConfig.internalDataDirPath = getFilesDir().getPath() + "/";
		AppConfig.basefiledir = AppConfig.datadirpath(_context) + "/.qemu/";
		AppConfig.ensureStoragePaths(_context);
		AppConfig.lastCrashLogPath = AppConfig.internalDataDirPath + "logs/lastcrash.txt";

        Config.cacheDir = _context.getCacheDir().getAbsolutePath();
        Config.storagedir = Environment.getExternalStorageDirectory().toString();
	}
}
