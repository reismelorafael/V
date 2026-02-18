package com.vectras.vm;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.qemu.utils.RamInfo;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.rafaelia.RafaeliaConfig;
import com.vectras.vm.rafaelia.RafaeliaQemuTuning;
import com.vectras.vm.rafaelia.RafaeliaSettings;
import com.vectras.vm.qemu.KvmProbe;
import com.vectras.vm.qemu.QemuArgsBuilder;
import com.vectras.vm.qemu.VmProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class StartVM {
    public static String cdrompath;
    public static volatile String lastResolvedProfile = "BALANCED";
    public static volatile boolean lastKvmEnabled = false;
    public static volatile String lastKvmReason = "unknown";

    public static String requiredQemuBinary(Context context) {
        return QemuArgsBuilder.binaryForArch(MainSettingsManager.getArch(context));
    }

    public static String env(Activity activity, String extras, String img, boolean isQuickRun) {

        String filesDir = activity.getFilesDir().getAbsolutePath();

        String[] qemu = new String[0];

        String bios = "";

        String finalextra = extras;

        ArrayList<String> params = new ArrayList<>(Arrays.asList(qemu));
        String arch = MainSettingsManager.getArch(activity);
        String ifType = "";

        if (!isQuickRun) {
            params.add(QemuArgsBuilder.binaryForArch(arch));

            params.add("-qmp");
            params.add("unix:" + Config.getLocalQMPSocketPath() + ",server,nowait");

            ifType = QemuArgsBuilder.resolveDriveInterface(activity, arch);

            String cdrom = "";
            String hdd0;
            String hdd1;

            if (!img.isEmpty()) {
                String backendImgPath = resolveBackendPath(activity, img, "rw");
                if (ifType.isEmpty()) {
                    hdd0 = "-hda";
                    hdd0 += " " + shellQuote(backendImgPath);
                } else {
                    hdd0 = "-drive";
                    hdd0 += " index=0";
                    hdd0 += ",media=disk";
                    hdd0 += ",readonly=off";
                    hdd0 += ",if=" + ifType;
                    hdd0 += ",file=" + shellQuote(backendImgPath);

                    if ((arch.equals("ARM64") && ifType.equals("ide")) || arch.equals("PPC")) {
                        hdd0 = "-drive";
                        hdd0 += " index=0";
                        hdd0 += ",media=disk";
                        hdd0 += ",readonly=off";
                        hdd0 += ",file=" + shellQuote(backendImgPath);
                    }
                }
                params.add(hdd0);
            }

            if (cdrompath.isEmpty()) {
                File cdromFile = new File(filesDir + "/data/Vectras/drive.iso");

                if (cdromFile.exists()) {
                    String backendCdromPath = resolveBackendPath(activity, cdromFile.getPath(), "r");
                    if (arch.equals("ARM64")) {
                        cdrom = " -drive";
                        cdrom += " if=none,id=cdrom,format=raw,media=cdrom,readonly=on,file=" + shellQuote(backendCdromPath);
                        cdrom += " -device";
                        cdrom += " usb-storage,drive=cdrom";
                        if (!extras.contains("-device nec-usb-xhci")) {
                            cdrom += " -device";
                            cdrom += " qemu-xhci";
                            cdrom += " -device";
                            cdrom += " nec-usb-xhci";
                        }
                    } else {
                        if (ifType.isEmpty()) {
                            cdrom = "-cdrom";
                            cdrom += " " + shellQuote(backendCdromPath);
                        } else {
                            cdrom = "-drive";
                            cdrom += " index=1";
                            cdrom += ",media=cdrom";
                            cdrom += ",readonly=on";
                            cdrom += ",file=" + shellQuote(backendCdromPath);
                        }
                    }

                    params.add(cdrom);
                }
            } else {
                String backendCdromPath = resolveBackendPath(activity, cdrompath, "r");
                if (arch.equals("ARM64")) {
                    cdrom += " -device";
                    cdrom += " nec-usb-xhci,id=defaultxhci";
                    cdrom += " -device";
                    cdrom += " usb-storage,bus=defaultxhci.0,drive=cdrom";
                    cdrom += " -drive";
                    cdrom += " if=none,id=cdrom,format=raw,media=cdrom,readonly=on,file=" + shellQuote(backendCdromPath);
                } else {
                    if (ifType.isEmpty()) {
                        cdrom = "-cdrom";
                        cdrom += " " + shellQuote(backendCdromPath);
                    } else {
                        cdrom = "-drive";
                        cdrom += " index=1";
                        cdrom += ",media=cdrom";
                        cdrom += ",readonly=on";
                        cdrom += ",file=" + shellQuote(backendCdromPath);
                    }
                }
                params.add(cdrom);
            }

            File hdd1File = new File(filesDir + "/data/Vectras/hdd1.qcow2");

            if (hdd1File.exists()) {
                String backendHdd1Path = resolveBackendPath(activity, hdd1File.getPath(), "rw");
                if (ifType.isEmpty()) {
                    hdd1 = "-hdb";
                    hdd1 += " " + shellQuote(backendHdd1Path);
                } else {
                    hdd1 = "-drive";
                    hdd1 += " index=2";
                    hdd1 += ",media=disk";
                    hdd1 += ",readonly=off";
                    hdd1 += ",if=" + ifType;
                    hdd1 += ",file=" + shellQuote(backendHdd1Path);
                }

                params.add(hdd1);
            }

            if (MainSettingsManager.get3dfxEnabled(activity)
                    && (arch.equals("X86_64")
                    || arch.equals("I386"))
                    && MainSettingsManager.getVmUi(activity).equals("X11")
                    && MainSettingsManager.getUseSdl(activity)) {
                String wrapperPath = get3dfxWrapperPath(activity);
                if (wrapperPath != null && !finalextra.contains(wrapperPath)) {
                    String backendWrapperPath = resolveBackendPath(activity, wrapperPath, "r");
                    String wrapperCdrom = "-drive index=4,media=cdrom,file=" + shellQuote(backendWrapperPath);
                    wrapperCdrom = wrapperCdrom.replace(",media=cdrom", ",media=cdrom,readonly=on");
                    params.add(wrapperCdrom);
                }
            }

            if (MainSettingsManager.getSharedFolder(activity) && !arch.equals("I386")) {
                String driveParams = "-drive ";
                if (ifType.isEmpty()) {
                    driveParams += "media=disk,file=fat:";
                } else {
                    driveParams += "index=3,media=disk,file=fat:";
                }
                driveParams += "rw:"; //Disk Drives are always Read/Write
                driveParams += FileUtils.getExternalFilesDirectory(activity).getPath() + "/SharedFolder,format=raw";
                params.add(driveParams);
            }

            String memoryStr = "-m ";
            if (arch.equals("PPC") && RamInfo.vectrasMemory(activity) > 2048) {
                memoryStr += 2048;
            } else {
                memoryStr += RamInfo.vectrasMemory(activity);
            }

            String boot = "-boot ";
            if (extras.contains(".iso ")) {

                boot += MainSettingsManager.getBoot(activity);
            } else {
                boot += "c";
            }

            //String soundDevice = "-audiodev pa,id=pa -device AC97,audiodev=pa";

            //params.add(soundDevice);

            if (MainSettingsManager.useDefaultBios(activity)) {
                if (arch.equals("PPC")) {
                    bios = "-L ";
                    bios += "pc-bios";
                } else if (arch.equals("ARM64")) {
                    bios = "-drive ";
                    bios += "file=" + AppConfig.basefiledir + "QEMU_EFI.img,format=raw,readonly=on,if=pflash";
                    bios += " -drive ";
                    bios += "file=" + AppConfig.basefiledir + "QEMU_VARS.img,format=raw,if=pflash";
                } else if (arch.equals("X86_64") && MainSettingsManager.getuseUEFI(activity)) {
                    bios = "-drive ";
                    bios += "file=" + AppConfig.basefiledir + "RELEASEX64_OVMF.fd,format=raw,readonly=on,if=pflash";
                    bios += " -drive ";
                    bios += "file=" + AppConfig.basefiledir + "RELEASEX64_OVMF_VARS.fd,format=raw,if=pflash";
                } else {
                    bios = "-bios ";
                    bios += AppConfig.basefiledir + "bios-vectras.bin";
                }
            }

            String machine = "-M ";
            if (Objects.equals(arch, "X86_64")) {
                machine += "pc";
                params.add(machine);
            } else if (Objects.equals(arch, "ARM64")) {
                machine += "virt";
                params.add(machine);
            }

            if (MainSettingsManager.useMemoryOvercommit(activity)) {
                params.add("-overcommit");
                params.add("mem-lock=off");
            }


            if (MainSettingsManager.useLocalTime(activity)) {
                params.add("-rtc");
                params.add("base=localtime");
            }

            //if (!arch.equals("PPC")) {
            //params.add("-nodefaults");
            //}

            //if (!Objects.equals(arch, "ARM64")) {
            params.add(bios);
            //}

            params.add(boot);

            params.add(memoryStr);

            finalextra = normalizeCdromArgumentStyle(extras, ifType);
        }

        RafaeliaConfig rafaeliaConfig = RafaeliaConfig.fromPreferences(activity);
        finalextra = RafaeliaQemuTuning.apply(finalextra, rafaeliaConfig);
        String rafaeliaArg = rafaeliaConfig.toQemuArgument();
        if (rafaeliaArg != null && !finalextra.contains("-rafaelia")) {
            params.add(rafaeliaArg);
        }

        if (rafaeliaConfig.getEnabled() && RafaeliaSettings.isLogCaptureEnabled(activity)) {
            if (!finalextra.contains("-d trace") && !finalextra.contains("-D ")) {
                params.add("-d");
                params.add("trace");
                params.add("-D");
                params.add(shellQuote(RafaeliaSettings.logFile(activity).getAbsolutePath()));
            }
        }

        VmProfile profile = QemuArgsBuilder.resolveProfile(activity, finalextra);
        QemuArgsBuilder.applyProfile(params, activity, finalextra);
        QemuArgsBuilder.applyVirtioStorageHints(params, arch, ifType, finalextra);
        QemuArgsBuilder.applyVirtioNet(params, finalextra);
        KvmProbe.ProbeResult kvmProbe = QemuArgsBuilder.applyAcceleration(params, finalextra);
        lastResolvedProfile = profile.name();
        lastKvmEnabled = kvmProbe.enabled;
        lastKvmReason = kvmProbe.reason;
        if (BuildConfig.DEBUG) {
            Log.i("StartVM", "QEMU profile=" + profile + " arch=" + arch
                    + " kvm=" + (kvmProbe.enabled ? "on" : "off") + " reason=" + kvmProbe.reason);
        }

        params.add(finalextra);

        if (isQuickRun) {
            params.add("-qmp");
            params.add("unix:" + Config.getLocalQMPSocketPath() + ",server,nowait");
        }

        if (MainSettingsManager.getVmUi(activity).equals("VNC")) {

            if (!MainSettingsManager.getVncExternalPassword(activity).isEmpty()) {
                params.add("-object ");
                params.add("secret,id=vncpass,data=\"" + MainSettingsManager.getVncExternalPassword(activity) + "\"");
            }

            String vncStr = "-vnc ";
            params.add(vncStr);
            // Allow connections only from localhost using localsocket without a password
            if (MainSettingsManager.getVncExternal(activity)) {

                String vncParams = Config.defaultVNCHost + ":" + Config.defaultVNCPort;

                if (!MainSettingsManager.getVncExternalPassword(activity).isEmpty()) vncParams += ",password-secret=vncpass";

                params.add(vncParams);
            } else {
                String vncSocketParams = "unix:";
                vncSocketParams += Config.getLocalVNCSocketPath();
                params.add(vncSocketParams);
            }

            //if (!arch.equals("PPC") || !arch.equals("ARM64")) {
            params.add("-monitor");
            params.add("vc");
            //}
        } else if (MainSettingsManager.getVmUi(activity).equals("SPICE")) {
            String spiceStr = "-spice ";
            spiceStr += "port=6999,disable-ticketing=on";
            params.add(spiceStr);
        } else if (MainSettingsManager.getVmUi(activity).equals("X11")) {
            params.add("-display");
            params.add(MainSettingsManager.getUseSdl(activity) ? "sdl" : "gtk" + ",gl=on");
            params.add("-monitor");
            params.add(MainSettingsManager.getRunQemuWithXterm(activity) ? "stdio" : "vc");
        }

        //params.add("-full-screen");

        return String.join(" ", params);
    }

    private static String get3dfxWrapperPath(Activity activity) {
        String customPath = MainSettingsManager.get3dfxWrapperPath(activity);
        if (customPath != null) {
            String trimmedCustomPath = customPath.trim();
            if (!trimmedCustomPath.isEmpty()) {
                File customFile = new File(trimmedCustomPath);
                if (customFile.exists()) {
                    return customFile.getPath();
                }
            }
        }
        String wrapperValue = MainSettingsManager.get3dfxWrapperVersion(activity);
        if (wrapperValue == null) {
            return null;
        }
        String trimmedValue = wrapperValue.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }
        File wrapperFile = new File(trimmedValue);
        if (!wrapperFile.isAbsolute()) {
            File downloadsFile = new File(AppConfig.downloadsFolder, trimmedValue);
            if (downloadsFile.exists()) {
                return downloadsFile.getPath();
            }
            File altDir = new File(AppConfig.maindirpath, "3dfx");
            File altFile = new File(altDir, trimmedValue);
            if (altFile.exists()) {
                return altFile.getPath();
            }
            File importedFile = new File(AppConfig.importedDriveFolder, trimmedValue);
            if (importedFile.exists()) {
                return importedFile.getPath();
            }
        }
        return wrapperFile.exists() ? wrapperFile.getPath() : null;
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static String normalizeCdromArgumentStyle(String extras, String ifType) {
        if (extras == null || extras.isEmpty()) {
            return extras;
        }

        ArrayList<ArgToken> tokens = tokenizeArguments(extras);
        if (tokens.isEmpty()) {
            return extras;
        }

        ArrayList<Replacement> replacements = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            ArgToken current = tokens.get(i);
            if ("-cdrom".equals(current.text) && i + 1 < tokens.size()) {
                ArgToken pathToken = tokens.get(i + 1);
                replacements.add(new Replacement(
                        current.start,
                        pathToken.end,
                        ifType.isEmpty()
                                ? "-cdrom " + pathToken.text
                                : "-drive index=1,media=cdrom,readonly=on,file=" + pathToken.text
                ));
                i++;
            } else if ("-drive".equals(current.text) && i + 1 < tokens.size()) {
                ArgToken driveToken = tokens.get(i + 1);
                String driveFile = extractCdromDriveFile(driveToken.text);
                if (driveFile != null) {
                    replacements.add(new Replacement(
                            current.start,
                            driveToken.end,
                            ifType.isEmpty()
                                    ? "-cdrom " + driveFile
                                    : "-drive index=1,media=cdrom,readonly=on,file=" + driveFile
                    ));
                    i++;
                }
            }
        }

        if (replacements.isEmpty()) {
            return extras;
        }

        Collections.sort(replacements, Comparator.comparingInt(r -> -r.start));
        StringBuilder normalized = new StringBuilder(extras);
        for (Replacement replacement : replacements) {
            normalized.replace(replacement.start, replacement.end, replacement.value);
        }
        return normalized.toString();
    }

    private static String extractCdromDriveFile(String driveSpec) {
        List<String> options = splitDriveOptions(driveSpec);
        String media = null;
        String file = null;
        for (String option : options) {
            int equalsIndex = option.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String key = option.substring(0, equalsIndex).trim();
            String value = option.substring(equalsIndex + 1).trim();
            if ("media".equals(key)) {
                media = value;
            } else if ("file".equals(key)) {
                file = value;
            }
        }
        if (!"cdrom".equals(media) || file == null || file.isEmpty()) {
            return null;
        }
        return file;
    }

    private static List<String> splitDriveOptions(String value) {
        ArrayList<String> options = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }

            if (c == ',' && !inSingleQuote && !inDoubleQuote) {
                options.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        options.add(current.toString());
        return options;
    }

    private static ArrayList<ArgToken> tokenizeArguments(String commandLine) {
        ArrayList<ArgToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < commandLine.length()) {
            while (index < commandLine.length() && Character.isWhitespace(commandLine.charAt(index))) {
                index++;
            }
            if (index >= commandLine.length()) {
                break;
            }

            int tokenStart = index;
            boolean inSingleQuote = false;
            boolean inDoubleQuote = false;
            while (index < commandLine.length()) {
                char c = commandLine.charAt(index);
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                    break;
                }
                index++;
            }
            int tokenEnd = index;
            tokens.add(new ArgToken(commandLine.substring(tokenStart, tokenEnd), tokenStart, tokenEnd));
        }
        return tokens;
    }

    private static final class ArgToken {
        final String text;
        final int start;
        final int end;

        ArgToken(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private static final class Replacement {
        final int start;
        final int end;
        final String value;

        Replacement(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    private static String resolveBackendPath(Activity activity, String path, String backendMode) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        if (!path.startsWith("content://") && !path.startsWith("/content/")) {
            return path;
        }

        int fd = FileUtils.get_fd(activity, path, backendMode);
        if (fd <= 0) {
            return path;
        }
        return "/proc/self/fd/" + fd;
    }

}
