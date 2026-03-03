package com.vectras.vm.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.vectras.vm.localization.LanguageModule;

import org.junit.Test;

import java.net.URI;
import java.util.List;

public class NetworkEndpointsTest {

    @Test
    public void romInfoEndpointsComposeExpectedUrls() {
        assertEquals(
                "https://go.anbui.ovh/egg/contentinfo?id=42&app=vectrasvm",
                NetworkEndpoints.romContentInfo("42", false)
        );

        assertEquals(
                "https://go.anbui.ovh/egg/contentinfo?id=42",
                NetworkEndpoints.romContentInfo("42", true)
        );

        assertEquals(
                "https://go.anbui.ovh/egg/updatelike?app=vectrasvm",
                NetworkEndpoints.romUpdateLike()
        );

        assertEquals(
                "https://go.anbui.ovh/egg/updateview?app=vectrasvm",
                NetworkEndpoints.romUpdateView()
        );
    }

    @Test
    public void githubUserViewEndpointsComposeExpectedApiAndProfileUrls() {
        assertEquals(
                "https://api.github.com/users/octocat",
                NetworkEndpoints.githubUserApi("octocat")
        );

        assertEquals(
                "https://github.com/octocat",
                NetworkEndpoints.githubUserProfile("octocat")
        );
    }

    @Test
    public void languageModulesUseRawGithubusercontentWithExpectedPath() {
        List<LanguageModule> modules = LanguageModule.Companion.getSupportedLanguages();

        for (LanguageModule module : modules) {
            if (module.isBuiltIn()) {
                continue;
            }

            URI uri = URI.create(NetworkEndpoints.languageModuleRaw(module.getLanguageCode()));
            assertEquals("https", uri.getScheme());
            assertEquals("raw.githubusercontent.com", uri.getHost());
            assertTrue(uri.getPath().startsWith("/rafaelmeloreisnovo/Vectras-VM-Android/main/resources/lang/"));
            assertTrue(uri.getPath().endsWith("/" + module.getLanguageCode() + ".json"));
            String expected = NetworkEndpoints.languageModuleRaw(module.getLanguageCode());
            assertEquals(expected, uri.toString());
        }
    }
}
