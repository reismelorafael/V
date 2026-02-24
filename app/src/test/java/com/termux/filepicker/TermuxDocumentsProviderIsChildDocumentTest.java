package com.termux.filepicker;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TermuxDocumentsProviderIsChildDocumentTest {

    private final TermuxDocumentsProvider provider = new TermuxDocumentsProvider();

    @Test
    public void acceptsSameCanonicalPathAsParent() throws IOException {
        Path parent = Files.createTempDirectory("termux-provider-parent");
        try {
            Assert.assertTrue(provider.isChildDocument(parent.toString(), parent.toString()));
        } finally {
            Files.deleteIfExists(parent);
        }
    }

    @Test
    public void acceptsCanonicalChildWithParentSeparatorBoundary() throws IOException {
        Path parent = Files.createTempDirectory("termux-provider-parent");
        Path child = Files.createDirectories(parent.resolve("nested/child"));
        try {
            Assert.assertTrue(provider.isChildDocument(parent.toString(), child.toString()));
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(child.getParent());
            Files.deleteIfExists(parent);
        }
    }

    @Test
    public void rejectsDotDotTraversalOutsideParent() throws IOException {
        Path base = Files.createTempDirectory("termux-provider-base");
        Path parent = Files.createDirectories(base.resolve("home/user"));
        Path outside = Files.createDirectories(base.resolve("home/outside"));
        Path escaped = parent.resolve("../outside");
        try {
            Assert.assertFalse(provider.isChildDocument(parent.toString(), escaped.toString()));
            Assert.assertTrue(Files.exists(outside));
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(parent);
            Files.deleteIfExists(parent.getParent());
            Files.deleteIfExists(base);
        }
    }

    @Test
    public void rejectsAbsolutePathOutsideParent() throws IOException {
        Path parent = Files.createTempDirectory("termux-provider-parent");
        Path outside = Files.createTempFile("termux-provider-outside", ".tmp");
        try {
            Assert.assertFalse(provider.isChildDocument(parent.toString(), outside.toAbsolutePath().toString()));
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(parent);
        }
    }

    @Test
    public void rejectsFalseStringPrefixPaths() throws IOException {
        Path base = Files.createTempDirectory("termux-provider-prefix");
        Path parent = Files.createDirectories(base.resolve("home/user"));
        Path fakePrefixSibling = Files.createDirectories(base.resolve("home/user2"));
        try {
            Assert.assertFalse(provider.isChildDocument(parent.toString(), fakePrefixSibling.toString()));
        } finally {
            Files.deleteIfExists(fakePrefixSibling);
            Files.deleteIfExists(parent);
            Files.deleteIfExists(parent.getParent());
            Files.deleteIfExists(base);
        }
    }

    @Test
    public void rejectsSymlinkEscapingParent() throws IOException {
        Path base = Files.createTempDirectory("termux-provider-symlink");
        Path parent = Files.createDirectories(base.resolve("home/user"));
        Path outside = Files.createDirectories(base.resolve("outside"));
        Path symlinkPath = parent.resolve("link-out");

        try {
            try {
                Files.createSymbolicLink(symlinkPath, outside);
            } catch (UnsupportedOperationException | SecurityException e) {
                Assume.assumeNoException("Symlink not supported in this environment", e);
            }

            Assert.assertFalse(provider.isChildDocument(parent.toString(), symlinkPath.toString()));
        } finally {
            Files.deleteIfExists(symlinkPath);
            Files.deleteIfExists(parent);
            Files.deleteIfExists(parent.getParent());
            Files.deleteIfExists(outside);
            Files.deleteIfExists(base);
        }
    }
}
