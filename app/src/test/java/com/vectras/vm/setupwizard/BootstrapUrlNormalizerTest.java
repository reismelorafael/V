package com.vectras.vm.setupwizard;

import org.junit.Assert;
import org.junit.Test;

public class BootstrapUrlNormalizerTest {

    @Test
    public void normalizesDuplicateSlashes() {
        Assert.assertEquals("/v1/bootstrap.tar.gz", BootstrapUrlNormalizer.normalizePath("//v1///bootstrap.tar.gz"));
    }

    @Test
    public void ensuresLeadingSlash() {
        Assert.assertEquals("/files/bootstrap.tar", BootstrapUrlNormalizer.normalizePath("files/bootstrap.tar"));
    }

    @Test
    public void defaultsToRootPath() {
        Assert.assertEquals("/", BootstrapUrlNormalizer.normalizePath(""));
        Assert.assertEquals("/", BootstrapUrlNormalizer.normalizePath(null));
    }
}
