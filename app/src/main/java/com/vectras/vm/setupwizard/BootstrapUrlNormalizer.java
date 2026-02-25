package com.vectras.vm.setupwizard;

final class BootstrapUrlNormalizer {
    private BootstrapUrlNormalizer() {
    }

    static String normalizePath(String encodedPath) {
        if (encodedPath == null || encodedPath.isEmpty()) {
            return "/";
        }

        String path = encodedPath;
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
}
