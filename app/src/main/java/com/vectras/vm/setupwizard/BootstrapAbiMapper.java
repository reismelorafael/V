package com.vectras.vm.setupwizard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class BootstrapAbiMapper {

    private static final Map<String, String[]> ABI_FALLBACKS = buildFallbacks();

    private BootstrapAbiMapper() {
    }

    static List<String> resolveCandidates(String[] deviceAbis) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (deviceAbis != null) {
            for (String abi : deviceAbis) {
                if (abi == null) {
                    continue;
                }
                String normalized = normalizeAbi(abi);
                addCandidate(ordered, normalized);
                String[] fallbacks = ABI_FALLBACKS.get(normalized);
                if (fallbacks != null) {
                    for (String fallback : fallbacks) {
                        addCandidate(ordered, fallback);
                    }
                }
            }
        }
        if (ordered.isEmpty()) {
            addCandidate(ordered, "arm64-v8a");
            addCandidate(ordered, "aarch64");
        }
        return new ArrayList<>(ordered);
    }

    static String architectureMetadataKey(String candidate) {
        String normalized = normalizeAbi(candidate);
        if ("arm64-v8a".equals(normalized) || "aarch64".equals(normalized)) {
            return "aarch64";
        }
        if ("armeabi-v7a".equals(normalized) || "arm".equals(normalized) || "armhf".equals(normalized)) {
            return "armhf";
        }
        if ("x86_64".equals(normalized) || "amd64".equals(normalized)) {
            return "amd64";
        }
        if ("x86".equals(normalized) || "i686".equals(normalized)) {
            return "x86";
        }
        return normalized;
    }

    private static void addCandidate(Set<String> ordered, String value) {
        if (value != null && !value.trim().isEmpty()) {
            ordered.add(value.trim());
        }
    }

    private static String normalizeAbi(String abi) {
        return abi == null ? "" : abi.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String[]> buildFallbacks() {
        LinkedHashMap<String, String[]> map = new LinkedHashMap<>();
        map.put("arm64-v8a", new String[]{"aarch64"});
        map.put("aarch64", new String[]{"arm64-v8a"});
        map.put("armeabi-v7a", new String[]{"arm", "armhf"});
        map.put("arm", new String[]{"armeabi-v7a", "armhf"});
        map.put("armhf", new String[]{"armeabi-v7a", "arm"});
        map.put("x86_64", new String[]{"amd64"});
        map.put("amd64", new String[]{"x86_64"});
        map.put("x86", new String[]{"i686"});
        map.put("i686", new String[]{"x86"});
        return map;
    }
}
