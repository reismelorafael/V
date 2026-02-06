package com.vectras.vm;

import android.app.Activity;
import android.os.Build;
import android.util.Log;

import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.qemu.utils.RamInfo;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.rafaelia.RafaeliaConfig;
import com.vectras.vm.rafaelia.RafaeliaQemuTuning;
import com.vectras.vm.rafaelia.RafaeliaSettings;
import com.vectras.vm.qemu.QemuArgsBuilder;
import com.vectras.vm.qemu.VmProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class StartVM {
    public static String cache;

    public static String cdrompath;

    public static String env(Activity activity, String extras, String img, boolean isQuickRun) {

        String filesDir = activity.getFilesDir().getAbsolutePath();

        String[] qemu = new String[0];

        String bios = "";

        String finalextra = extras;

        ArrayList<String> params = new ArrayList<>(Arrays.asList(qemu));

        if (!isQuickRun) {
            String arch = MainSettingsManager.getArch(activity);
            params.add(QemuArgsBuilder.binaryForArch(arch));

            params.add("-qmp");
            params.add("unix:" + Config.getLocalQMPSocketPath() + ",server,nowait");

            String ifType = QemuArgsBuilder.resolveDriveInterface(activity, MainSettingsManager.getArch(activity));

            String cdrom = "";
            String hdd0;
            String hdd1;

            if (!img.isEmpty()) {
                if (ifType.isEmpty()) {
                    hdd0 = "-hda";
                    hdd0 += " '" + img + "'";
                } else {
                    hdd0 = "-drive";
                    hdd0 += " index=0";
                    hdd0 += ",media=disk";
                    hdd0 += ",if=" + ifType;
                    hdd0 += ",file='" + img + "'";

                    if ((MainSettingsManager.getArch(activity).equals("ARM64") && ifType.equals("ide")) || MainSettingsManager.getArch(activity).equals("PPC")) {
                        hdd0 = "-drive";
                        hdd0 += " index=0";
                        hdd0 += ",media=disk";
                        hdd0 += ",file='" + img + "'";
                    }
                }
                params.add(hdd0);
            }

            if (cdrompath.isEmpty()) {
                File cdromFile = new File(filesDir + "/data/Vectras/drive.iso");

                if (cdromFile.exists()) {
                    if (MainSettingsManager.getArch(activity).equals("ARM64")) {
                        cdrom = " -drive";
                        cdrom += " if=none,id=cdrom,format=raw,media=cdrom,file='" + cdromFile.getPath() + "'";
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
                            cdrom += " '" + cdromFile.getPath() + "'";
                        } else {
                            cdrom = "-drive";
                            cdrom += " index=1";
                            cdrom += ",media=cdrom";
                            cdrom += ",file='" + cdromFile.getPath() + "'";
                        }
                    }

                    params.add(cdrom);
                }
            } else {
                if (MainSettingsManager.getArch(activity).equals("ARM64")) {
                    cdrom += " -device";
                    cdrom += " nec-usb-xhci,id=defaultxhci";
                    cdrom += " -device";
                    cdrom += " usb-storage,bus=defaultxhci.0,drive=cdrom";
                    cdrom += " -drive";
                    cdrom += " if=none,id=cdrom,format=raw,media=cdrom,file='" + cdrompath + "'";
                } else {
                    if (ifType.isEmpty()) {
                        cdrom = "-cdrom";
                        cdrom += " '" + cdrompath + "'";
                    } else {
                        cdrom = "-drive";
                        cdrom += " index=1";
                        cdrom += ",media=cdrom";
                        cdrom += ",file='" + cdrompath + "'";
                    }
                }
                params.add(cdrom);
            }

            File hdd1File = new File(filesDir + "/data/Vectras/hdd1.qcow2");

            if (hdd1File.exists()) {
                if (ifType.isEmpty()) {
                    hdd1 = "-hdb";
                    hdd1 += " '" + hdd1File.getPath() + "'";
                } else {
                    hdd1 = "-drive";
                    hdd1 += " index=2";
                    hdd1 += ",media=disk";
                    hdd1 += ",if=" + ifType;
                    hdd1 += ",file='" + hdd1File.getPath() + "'";
                }

                params.add(hdd1);
            }

            if (MainSettingsManager.get3dfxEnabled(activity)
                    && (MainSettingsManager.getArch(activity).equals("X86_64")
                    || MainSettingsManager.getArch(activity).equals("I386"))
                    && MainSettingsManager.getVmUi(activity).equals("X11")
                    && MainSettingsManager.getUseSdl(activity)) {
                String wrapperPath = get3dfxWrapperPath(activity);
                if (wrapperPath != null && !finalextra.contains(wrapperPath)) {
                    String wrapperCdrom = "-drive index=4,media=cdrom,file='" + wrapperPath + "'";
                    params.add(wrapperCdrom);
                }
            }

            if (MainSettingsManager.getSharedFolder(activity) && !MainSettingsManager.getArch(activity).equals("I386")) {
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
            if (MainSettingsManager.getArch(activity).equals("PPC") && RamInfo.vectrasMemory(activity) > 2048) {
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
                if (MainSettingsManager.getArch(activity).equals("PPC")) {
                    bios = "-L ";
                    bios += "pc-bios";
                } else if (MainSettingsManager.getArch(activity).equals("ARM64")) {
                    bios = "-drive ";
                    bios += "file=" + AppConfig.basefiledir + "QEMU_EFI.img,format=raw,readonly=on,if=pflash";
                    bios += " -drive ";
                    bios += "file=" + AppConfig.basefiledir + "QEMU_VARS.img,format=raw,if=pflash";
                } else if (MainSettingsManager.getArch(activity).equals("X86_64") && MainSettingsManager.getuseUEFI(activity)) {
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
            if (Objects.equals(MainSettingsManager.getArch(activity), "X86_64")) {
                machine += "pc";
                params.add(machine);
            } else if (Objects.equals(MainSettingsManager.getArch(activity), "ARM64")) {
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

            //if (!MainSettingsManager.getArch(activity).equals("PPC")) {
            //params.add("-nodefaults");
            //}

            //if (!Objects.equals(MainSettingsManager.getArch(activity), "ARM64")) {
            params.add(bios);
            //}

            params.add(boot);

            params.add(memoryStr);

            if (ifType.isEmpty()) {
                if (extras.contains("-drive index=1,media=cdrom,file=")) {
                    finalextra = extras.replace("-drive index=1,media=cdrom,file=", "-cdrom ");
                }
            } else {
                if (extras.contains("-cdrom ")) {
                    finalextra = extras.replace("-cdrom ", "-drive index=1,media=cdrom,file=");
                }
            }
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
                params.add("'" + RafaeliaSettings.logFile(activity).getAbsolutePath() + "'");
            }
        }

        VmProfile profile = QemuArgsBuilder.resolveProfile(activity, finalextra);
        QemuArgsBuilder.applyProfile(params, activity, finalextra);
        QemuArgsBuilder.applyVirtioNet(params, finalextra);
        QemuArgsBuilder.applyAcceleration(params);
        if (BuildConfig.DEBUG) {
            Log.i("StartVM", "QEMU profile=" + profile + " arch=" + MainSettingsManager.getArch(activity));
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

            //if (!MainSettingsManager.getArch(activity).equals("PPC") || !MainSettingsManager.getArch(activity).equals("ARM64")) {
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

}
