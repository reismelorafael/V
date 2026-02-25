package com.vectras.vm.network;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class EndpointValidator {

    private static final Set<String> DEFAULT_ALLOWLIST;

    static {
        Set<String> hosts = new HashSet<>();
        hosts.add(NetworkEndpoints.HOST_ANBUI);
        hosts.add(NetworkEndpoints.HOST_GITHUB_API);
        hosts.add(NetworkEndpoints.HOST_GITHUB_WEB);
        hosts.add(NetworkEndpoints.HOST_GITHUB_RAW);
        DEFAULT_ALLOWLIST = Collections.unmodifiableSet(hosts);
    }

    private EndpointValidator() {
    }

    public static boolean isAllowed(@NonNull String url) {
        return isAllowed(url, DEFAULT_ALLOWLIST);
    }

    public static boolean isValidHttpUrl(@NonNull String url) {
        return isAllowed(url);
    }

    public static void requireValidHttpUrl(@NonNull String url, @NonNull String label) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException(label + " rejected by endpoint allowlist");
        }
    }

    public static boolean isAllowed(@NonNull String url, @NonNull Set<String> hostAllowlist) {
        if (url.trim().isEmpty()) {
            return false;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ignored) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        if (host == null || host.trim().isEmpty()) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!hostAllowlist.contains(normalizedHost)) {
            return false;
        }

        if (port != -1 && port != 443) {
            return false;
        }

        return uri.getUserInfo() == null;
    }
}
