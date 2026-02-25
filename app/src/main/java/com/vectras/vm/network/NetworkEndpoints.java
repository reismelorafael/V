package com.vectras.vm.network;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class NetworkEndpoints {

    public static final String HOST_ANBUI = "go.anbui.ovh";
    public static final String HOST_GITHUB_API = "api.github.com";
    public static final String HOST_GITHUB_WEB = "github.com";
    public static final String HOST_GITHUB_RAW = "raw.githubusercontent.com";

    private static final String SCHEME = "https://";
    private static final String APP_ID = "vectrasvm";

    private NetworkEndpoints() {
    }

    public static String romContentInfo(@NonNull String contentId, boolean isAnBuiId) {
        String appQuery = isAnBuiId ? "" : "&app=" + APP_ID;
        return SCHEME + HOST_ANBUI + "/egg/contentinfo?id=" + contentId + appQuery;
    }

    public static String romUpdateLike() {
        return SCHEME + HOST_ANBUI + "/egg/updatelike?app=" + APP_ID;
    }

    public static String romUpdateView() {
        return SCHEME + HOST_ANBUI + "/egg/updateview?app=" + APP_ID;
    }

    public static String githubUserApi(@NonNull String username) {
        return SCHEME + HOST_GITHUB_API + "/users/" + username;
    }

    public static String githubUserProfile(@NonNull String username) {
        return SCHEME + HOST_GITHUB_WEB + "/" + username;
    }

    public static String termuxPulseAudioScript() {
        return SCHEME + HOST_GITHUB_RAW + "/rafaelmeloreisnovo/Vectras-VM-Android/main/resources/scripts/setup-termux-audio.sh";
    }

    public static String termuxReleasePage() {
        return SCHEME + HOST_GITHUB_WEB + "/termux/termux-app/releases";
    }

    public static String githubProfile(@NonNull String username) {
        return githubUserProfile(username);
    }

    public static String languageModuleRaw(@NonNull String languageCode) {
        String normalized = languageCode.toLowerCase(Locale.ROOT);
        return SCHEME + HOST_GITHUB_RAW + "/rafaelmeloreisnovo/Vectras-VM-Android/main/resources/lang/" + normalized + ".json";
    }
}
