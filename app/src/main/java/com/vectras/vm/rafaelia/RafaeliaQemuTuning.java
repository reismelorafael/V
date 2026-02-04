package com.vectras.vm.rafaelia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RafaeliaQemuTuning {
    private static final int DEFAULT_TB_SIZE = 2048;
    private static final Pattern ACCEL_TCG_PATTERN = Pattern.compile("(?<!\\S)-accel\\s+tcg[^\\s]*");

    private RafaeliaQemuTuning() {
    }

    public static String apply(String extras, RafaeliaConfig config) {
        if (extras == null || extras.isBlank()) {
            return extras;
        }
        if (config == null || !config.getEnabled()) {
            return extras;
        }
        if (!config.getAutotuneEnabled()) {
            return extras;
        }
        int tbSize = config.getTcgTbSize() > 0 ? config.getTcgTbSize() : DEFAULT_TB_SIZE;
        return ensureTcgTbSize(extras, tbSize);
    }

    private static String ensureTcgTbSize(String extras, int tbSize) {
        Matcher matcher = ACCEL_TCG_PATTERN.matcher(extras);
        StringBuffer updated = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String accel = matcher.group();
            if (accel.contains("tb-size=")) {
                matcher.appendReplacement(updated, Matcher.quoteReplacement(accel));
                continue;
            }
            String tuned = accel + ",tb-size=" + tbSize;
            matcher.appendReplacement(updated, Matcher.quoteReplacement(tuned));
            changed = true;
        }
        if (!changed) {
            return extras;
        }
        matcher.appendTail(updated);
        return updated.toString();
    }
}
