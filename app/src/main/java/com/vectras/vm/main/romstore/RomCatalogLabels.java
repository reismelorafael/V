package com.vectras.vm.main.romstore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class RomCatalogLabels {

    private RomCatalogLabels() {
    }

    @NonNull
    public static String buildSummaryLine(@Nullable String romArch,
                                          @Nullable String fileSize,
                                          @Nullable String osFamily,
                                          @Nullable String osFlavor,
                                          @Nullable String releaseChannel) {
        String base = safe(romArch) + " - " + safe(fileSize);
        if (isRafLinuxEnterprise(osFamily, osFlavor, releaseChannel)) {
            return "RafLinux Enterprise • " + base;
        }
        return base;
    }

    public static boolean isRafLinuxEnterprise(@Nullable String osFamily,
                                                @Nullable String osFlavor,
                                                @Nullable String releaseChannel) {
        String family = normalize(osFamily);
        String flavor = normalize(osFlavor);
        String channel = normalize(releaseChannel);

        if (!"linux".equals(family)) {
            return false;
        }

        return flavor.contains("raflinux") && (flavor.contains("enterprise") || channel.contains("enterprise") || channel.contains("lts"));
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.US);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "N/A";
        }
        return value;
    }
}
