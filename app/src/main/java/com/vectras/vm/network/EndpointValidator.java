package com.vectras.vm.network;

import java.net.URI;
import java.net.URISyntaxException;

public final class EndpointValidator {
    private EndpointValidator() {
    }

    public static boolean isValidHttpUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.trim().isEmpty()) {
                return false;
            }
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String requireValidHttpUrl(String url, String fieldName) {
        if (!isValidHttpUrl(url)) {
            throw new IllegalArgumentException("Invalid endpoint for " + fieldName + ": " + String.valueOf(url));
        }
        return url;
    }
}
