package com.vectras.vm.setupwizard;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class BootstrapAbiMapperTest {

    @Test
    public void resolvesArm64AliasesInStableOrder() {
        List<String> candidates = BootstrapAbiMapper.resolveCandidates(new String[]{"arm64-v8a", "armeabi-v7a"});

        Assert.assertEquals("arm64-v8a", candidates.get(0));
        Assert.assertTrue(candidates.contains("aarch64"));
        Assert.assertTrue(candidates.contains("arm"));
        Assert.assertTrue(candidates.contains("armhf"));
    }

    @Test
    public void resolvesX86Aliases() {
        List<String> candidates = BootstrapAbiMapper.resolveCandidates(new String[]{"x86_64", "x86"});

        Assert.assertTrue(candidates.contains("x86_64"));
        Assert.assertTrue(candidates.contains("amd64"));
        Assert.assertTrue(candidates.contains("x86"));
        Assert.assertTrue(candidates.contains("i686"));
    }

    @Test
    public void mapsMetadataArchitectureKey() {
        Assert.assertEquals("aarch64", BootstrapAbiMapper.architectureMetadataKey("arm64-v8a"));
        Assert.assertEquals("armhf", BootstrapAbiMapper.architectureMetadataKey("armeabi-v7a"));
        Assert.assertEquals("amd64", BootstrapAbiMapper.architectureMetadataKey("x86_64"));
        Assert.assertEquals("x86", BootstrapAbiMapper.architectureMetadataKey("x86"));
    }
}
