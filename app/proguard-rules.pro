# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.slf4j.impl.StaticLoggerBinder

# Preference fragments instantiated by class name from XML.
-keep class com.vectras.qemu.MainSettingsManager$AppPreferencesFragment { public <init>(); }
-keep class com.vectras.qemu.MainSettingsManager$QemuPreferencesFragment { public <init>(); }

# Legacy compatibility layer loaded reflectively in BCFactory.
-keep class com.antlersoft.android.bc.BCActivityManagerV5 { public <init>(); }
-keep class com.antlersoft.android.bc.BCHapticDefault { public <init>(); }
-keep class com.antlersoft.android.bc.BCMotionEvent4 { public <init>(); }
-keep class com.antlersoft.android.bc.BCMotionEvent5 { public <init>(); }
-keep class com.antlersoft.android.bc.BCStorageContext7 { public <init>(); }
-keep class com.antlersoft.android.bc.BCStorageContext8 { public <init>(); }

# Class names consumed by XML intent/accessibility metadata.
-keepnames class com.vectras.vm.settings.LanguageModulesActivity
-keepnames class com.vectras.vm.settings.VNCSettingsActivity
-keepnames class com.vectras.vm.settings.X11DisplaySettingsActivity
-keepnames class com.vectras.vm.settings.ImportExportSettingsActivity
-keepnames class com.vectras.vm.settings.UpdaterActivity
-keepnames class com.vectras.vm.x11.LoriePreferences

# Firebase Realtime Database generic mapper.
-keep class * extends com.google.firebase.database.GenericTypeIndicator { *; }
