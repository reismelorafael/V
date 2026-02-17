package com.vectras.vm;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.utils.DeviceUtils;

import java.io.File;
import java.util.Objects;

/**
 * @author dev
 */
public class AppConfig {

    private static final String LEGACY_FALLBACK_MAIN_DIR = "/sdcard/Documents/VectrasVM/";

    static {
        ensureStoragePaths(null);
    }

    // App Config
    public static String vectrasVersion = BuildConfig.VERSION_NAME;
    public static int vectrasVersionCode = BuildConfig.VERSION_CODE;
    public static final int standardSetupVersion = 9222;
    public static String vectrasWebsite = "https://vectras.vercel.app/";
    public static String vectrasWebsiteRaw = "https://raw.githubusercontent.com/AnBui2004/Vectras-VM-Emu-Android/refs/heads/master/web/";
    public static String bootstrapfileslink = vectrasWebsiteRaw + "/data/setupfiles3.json";
    public static String vectrasHelp = vectrasWebsite + "how.html";
    public static String community = vectrasWebsite + "community.html";
    public static String vectrasRaw = vectrasWebsiteRaw + "data/";
    public static String vectrasLicense = vectrasRaw + "LICENSE.md";
    public static String vectrasPrivacy = vectrasRaw + "PRIVACYANDPOLICY.md";
    public static String vectrasTerms = vectrasRaw + "TERMSOFSERVICE.md";
    public static String vectrasInfo = vectrasRaw + "info.md";
    public static String vectrasRepo = "https://github.com/xoureldeen/Vectras-VM-Android";
    public static String rafaeliaQemuRepo = "https://github.com/rafaelmeloreisnovo/qemu_rafaelia";
    public static String rafaeliaAndroidxRepo = "https://github.com/rafaelmeloreisnovo/androidx_RmR";
    public static String qemuDocsUrl = rafaeliaQemuRepo;
    public static String updateJson = vectrasRaw + "UpdateConfig.json";
    public static String blogJson = vectrasRaw + "news_list.json";
    // public static final String storeJson = vectrasRaw + "store_list.json";
    public static String storeJson = vectrasWebsiteRaw + "store_list.json";

    public static String releaseUrl = vectrasWebsite;

    public static String getSetupFiles() {
        String abi = Build.SUPPORTED_ABIS[0];
        return releaseUrl + "vectras-vm-" + abi + ".tar.gz";
    }

    public static String romsJson(Activity activity) {
        if (Objects.equals(MainSettingsManager.getArch(activity), "X86_64")) {
            return vectrasRaw + "roms-x86_64.json";
        } else if (Objects.equals(MainSettingsManager.getArch(activity), "I386")) {
            return vectrasRaw + "roms-i386.json";
        } else if (Objects.equals(MainSettingsManager.getArch(activity), "ARM64")) {
            return vectrasRaw + "roms-aarch64.json";
        } else if (Objects.equals(MainSettingsManager.getArch(activity), "PPC")) {
            return vectrasRaw + "roms-ppc.json";
        } else {
            return null;
        }
    }

    // App config
    public static String datadirpath(Context activity) {
        File externalDataDir = activity.getExternalFilesDir("data");
        if (externalDataDir != null) {
            return new File(externalDataDir, "Vectras").getPath();
        }
        return new File(activity.getFilesDir(), "data/Vectras").getPath();
        //return FileUtils.getExternalFilesDirectory(activity).getPath();
    }
    public static String internalDataDirPath = "/data/data/com.vectras.vm/files/";
    public static String basefiledir = "";
    public static String maindirpath = "";
    public static String recyclebin = "";
    //public static String basefiledir = datadirpath(SplashActivity.activity) + "/.qemu/";
    //public static String maindirpath = FileUtils.getExternalFilesDirectory(SplashActivity.activity).getPath() + "/";
    public static String sharedFolder = maindirpath + "SharedFolder/";
    public static String downloadsFolder = maindirpath + "Downloads/";
    public static String romsdatajson = maindirpath + "roms-data.json";
    public static String vmFolder = maindirpath + "roms/";
    public static String importedDriveFolder = maindirpath + "drive/";
    public static String cvbiFolder = maindirpath + "cvbi/";
    public static String pendingCommand = "";
    public static boolean engineHeadlessMode = false;
    public static String lastCrashLogPath = internalDataDirPath + "logs/lastcrash.txt";

    public static String neededPkgsAlpine() {
        if (DeviceUtils.isArm()) {
            return "bash aria2 tar fluxbox xterm libslirp libpulse libseccomp pipewire liburing" +
                    " sdl2 sdl2_image libepoxy virglrenderer" +
                    " libusb libaio ncurses-libs curl libnfs" +
                    " mesa-dri-gallium mesa-vulkan-swrast vulkan-loader mesa-utils mesa-egl mesa-gbm mesa-vulkan-freedreno" +
                    " qemu-audio-sdl snappy lzo ndctl vde2-libs sndio-libs linux-pam fuse3-libs libssh vte3";
        } else {
            return "bash aria2 tar fluxbox xterm libslirp libpulse libseccomp pipewire liburing" +
                    " sdl2 sdl2_image libepoxy virglrenderer" +
                    " libusb libaio ncurses-libs curl libnfs" +
                    " mesa-dri-gallium mesa-vulkan-swrast vulkan-loader mesa-utils mesa-egl mesa-gbm" +
                    " qemu-audio-sdl snappy lzo ndctl vde2-libs sndio-libs linux-pam fuse3-libs libssh vte3";
        }
    }

    public static String neededPkgs32bitAlpine() {
        if (DeviceUtils.isArm()) {
            return "bash aria2 tar dwm xterm libslirp libslirp-dev pulseaudio-dev glib-dev pixman-dev zlib-dev spice-dev" +
                    " libusbredirparser usbredir-dev sdl2 sdl2-dev sdl2_image-dev libepoxy-dev virglrenderer-dev rdma-core fluxbox" +
                    " libusb libaio ncurses-libs curl libnfs gtk+3.0 gtk+3.0-dev fuse libpulse libseccomp jack pipewire liburing pulseaudio pulseaudio-alsa alsa-plugins-pulse" +
                    " mesa-dri-gallium mesa-vulkan-swrast vulkan-loader mesa-utils mesa-egl" +
                    " qemu-audio-sdl capstone libcbor snappy lzo ndctl keyutils-libs vde2-libs libdw libbpf sndio-libs linux-pam fuse3-libs libssh vte3";
        } else {
            return "bash aria2 tar dwm xterm libslirp libslirp-dev pulseaudio-dev glib-dev pixman-dev zlib-dev spice-dev" +
                    " libusbredirparser usbredir-dev sdl2 sdl2-dev sdl2_image-dev libepoxy-dev virglrenderer-dev rdma-core fluxbox" +
                    " libusb libaio ncurses-libs curl libnfs gtk+3.0 gtk+3.0-dev fuse libpulse libseccomp jack pipewire liburing pulseaudio pulseaudio-alsa alsa-plugins-pulse" +
                    " mesa-dri-gallium mesa-vulkan-swrast vulkan-loader mesa-utils mesa-egl" +
                    " qemu-audio-sdl capstone libcbor snappy lzo ndctl keyutils-libs vde2-libs libdw libbpf sndio-libs linux-pam fuse3-libs libssh vte3 libatomic";
        }
    }

    public static String neededPkgsDebianUbuntu() {
        return "bash aria2 tar dwm xterm libslirp0 libslirp-dev libpulse-dev libglib2.0-dev libpixman-1-dev zlib1g-dev libspice-server-dev" +
                " libusbredirparser1 libusbredirparser-dev libsdl2-2.0-0 libsdl2-dev libsdl2-image-dev libepoxy-dev libvirglrenderer-dev rdma-core fluxbox" +
                " libusb-1.0-0 libaio1 libncurses6 curl libnfs-utils libgtk-3-0 libgtk-3-dev fuse3 libpulse0 libseccomp2 jackd2 pipewire liburing2 pulseaudio" +
                " mesa-vulkan-drivers vulkan-tools libegl1 libgbm1 qemu-system-gui libcapstone4 libcbor0.8 libsnappy1v5 liblzo2-2 ndctl keyutils" +
                " libdw1 libbpf1 libpam0g libssh-4 libvte-2.91-0";
    }

    public static String neededPkgs32bitDebianUbuntu() {
        return neededPkgsDebianUbuntu();
    }

    public static String neededPkgsTermux() {
        if (DeviceUtils.isArm()) {
            return "bash aria2 tar xterm proot pulseaudio";
        }
        return "bash aria2 tar xterm proot pulseaudio";
    }

    public static String neededPkgs32bitTermux() {
        return neededPkgsTermux();
    }

    // Backward-compatible aliases.
    public static String neededPkgs() {
        return neededPkgsAlpine();
    }

    public static String neededPkgs32bit() {
        return neededPkgs32bitAlpine();
    }

    public static boolean needreinstallsystem = false;

    public static String temporaryLastedTerminalOutput = "";

    public static String patreonLink = "https://www.patreon.com/VectrasTeam";

}
