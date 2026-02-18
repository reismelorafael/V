package com.vectras.vm.utils;

import android.app.Activity;

import com.vectras.vm.VectrasApp;
import com.vectras.vterm.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CommandUtils {
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\\.([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*$"
    );
    private static final Pattern PATH_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._~-]+$");

    public static String createForSelectedMirror(boolean _https, String _url, String _beforemain) {
        String version = "v3.19";
        String scheme = _https ? "https" : "http";
        String validatedUrl = normalizeMirrorHostAndPath(_url, "_url");
        String validatedBeforeMain = normalizeAbsolutePath(_beforemain, "_beforemain");
        String prefix = joinPaths(validatedUrl, validatedBeforeMain);

        List<String> repositories = new ArrayList<>();
        repositories.add(scheme + "://" + prefix + "/" + version + "/main");
        repositories.add(scheme + "://" + prefix + "/" + version + "/community");
        repositories.add(scheme + "://" + prefix + "/edge/testing");

        StringBuilder command = new StringBuilder("printf '%s\\n'");
        for (String repository : repositories) {
            command.append(" ").append(shellSingleQuote(repository));
        }
        command.append(" > /etc/apk/repositories");
        return command.toString();
    }

    private static String normalizeMirrorHostAndPath(String value, String argumentName) {
        if (value == null) {
            throw new IllegalArgumentException(argumentName + " must not be null.");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(argumentName + " must not be empty.");
        }
        if (trimmed.contains("\\") || trimmed.contains("?") || trimmed.contains("#") || trimmed.contains("@") || trimmed.contains(":")) {
            throw new IllegalArgumentException(argumentName + " contains forbidden URL characters.");
        }

        String[] pieces = trimmed.split("/", -1);
        String host = pieces[0];
        if (!HOST_PATTERN.matcher(host).matches()) {
            throw new IllegalArgumentException(argumentName + " host is invalid.");
        }

        if (pieces.length == 1) {
            return host;
        }

        StringBuilder normalized = new StringBuilder(host);
        for (int i = 1; i < pieces.length; i++) {
            String segment = pieces[i];
            validatePathSegment(segment, argumentName);
            normalized.append("/").append(segment);
        }
        return normalized.toString();
    }

    private static String normalizeAbsolutePath(String value, String argumentName) {
        if (value == null) {
            throw new IllegalArgumentException(argumentName + " must not be null.");
        }
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("/")) {
            throw new IllegalArgumentException(argumentName + " must start with '/'.");
        }
        if (value.contains("//") || value.contains("\\") || value.contains("?") || value.contains("#") || value.contains(":")) {
            throw new IllegalArgumentException(argumentName + " contains forbidden path characters.");
        }

        String[] pieces = value.substring(1).split("/", -1);
        StringBuilder normalized = new StringBuilder();
        for (String segment : pieces) {
            validatePathSegment(segment, argumentName);
            normalized.append("/").append(segment);
        }
        return normalized.toString();
    }

    private static void validatePathSegment(String segment, String argumentName) {
        if (segment.isEmpty()) {
            throw new IllegalArgumentException(argumentName + " contains an empty path segment.");
        }
        if (".".equals(segment) || "..".equals(segment) || !PATH_SEGMENT_PATTERN.matcher(segment).matches()) {
            throw new IllegalArgumentException(argumentName + " contains an invalid path segment.");
        }
    }

    private static String joinPaths(String left, String right) {
        if (right == null || right.isEmpty()) {
            return left;
        }
        return left + right;
    }

    public static String shellSingleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static void run(String _command, boolean _isShowResult, Activity _activity) {
        Terminal vterm = new Terminal(_activity);
        vterm.executeShellCommand2(_command, _isShowResult, _activity);
    }

    public static String getQemuVersionName() {
        String qemuVersion = getQemuVersion();

        if (qemuVersion.toLowerCase().contains("failed") || qemuVersion.toLowerCase().contains("not found"))
            return "";

        return (qemuVersion.contains("Error") ? qemuVersion.substring(0, qemuVersion.indexOf("Error")) : qemuVersion) + (is3dfxVersion() ? " - 3dfx" : "");
    }

    public static String getQemuVersion() {
        return VectrasApp.getContext() == null ? "Unknow" : Terminal.executeShellCommandWithResult("qemu-system-x86_64 --version | head -n1 | awk '{print $4}'", VectrasApp.getContext()).replace("\n", "");
    }

    public static boolean is3dfxVersion() {
        return VectrasApp.getContext() != null && Terminal.executeShellCommandWithResult("qemu-system-x86_64 --version", VectrasApp.getContext()).contains("3dfx");
    }
}
