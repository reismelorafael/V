package com.vectras.vm.network;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NetworkEndpoints {
    private static final String APP_FEATURE = "vectrasvm";
    private static final String EGG_BASE = "https://go.anbui.ovh/egg";
    private static final String GITHUB_API_USERS_BASE = "https://api.github.com/users";
    private static final String GITHUB_PROFILE_BASE = "https://github.com";

    private NetworkEndpoints() {
    }

    public static String romContentInfo(String contentId, boolean isAnBuiId) {
        StringBuilder url = new StringBuilder(EGG_BASE)
                .append("/contentinfo?id=")
                .append(encode(contentId));
        if (!isAnBuiId) {
            url.append("&app=").append(APP_FEATURE);
        }
        return url.toString();
    }

    public static String romUpdateLike() {
        return EGG_BASE + "/updatelike?app=" + APP_FEATURE;
    }

    public static String romUpdateView() {
        return EGG_BASE + "/updateview?app=" + APP_FEATURE;
    }

    public static String githubApiUser(String username) {
        return GITHUB_API_USERS_BASE + "/" + encodePathSegment(username);
    }

    public static String githubProfile(String username) {
        return GITHUB_PROFILE_BASE + "/" + encodePathSegment(username);
    }

    public static String termuxAppReleases() {
        return GITHUB_PROFILE_BASE + "/termux/termux-app/releases";
    }

    public static String termuxPulseAudioInstallScript() {
        return "https://raw.githubusercontent.com/AnBui2004/termux/refs/heads/main/installpulseaudio.sh";
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ignored) {
            return "";
        }
    }

    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }
}
