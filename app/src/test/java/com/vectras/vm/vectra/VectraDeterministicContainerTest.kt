package com.vectras.vm.vectra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectraDeterministicContainerTest {

    @Test
    fun container_normalizesPaths_andReadsDeterministically() {
        val container = VectraDeterministicContainer()
        val payload = "kernel-image".toByteArray()

        val entry = container.put("/System/Boot/../Boot/KERNEL.BIN", payload, preferredLayer = 2)
        val restored = container.read("system/boot/kernel.bin")

        assertEquals("system/boot/kernel.bin", entry.normalizedPath)
        assertEquals(2, entry.layer)
        assertTrue(entry.pathVectorBase60.matches(Regex("[0-9A-Za-x]+")))
        assertArrayEquals(payload, restored)
    }

    @Test
    fun manifest_isStableForSameData_andTracksIops() {
        val a = VectraDeterministicContainer()
        val b = VectraDeterministicContainer()

        a.put("os/bin/init", ByteArray(5000) { (it and 0x7F).toByte() }, preferredLayer = 0)
        a.put("os/etc/config", "deterministic".toByteArray(), preferredLayer = 1)

        b.put("OS/etc/config", "deterministic".toByteArray(), preferredLayer = 1)
        b.put("os/bin/init", ByteArray(5000) { (it and 0x7F).toByte() }, preferredLayer = 0)

        val m1 = a.buildManifest()
        val m2 = b.buildManifest()

        assertEquals(m1.totalBytes, m2.totalBytes)
        assertEquals(m1.totalChunks, m2.totalChunks)
        assertEquals(m1.entries.map { it.normalizedPath }, m2.entries.map { it.normalizedPath })
        assertEquals(m1.entries.map { it.entryTag }, m2.entries.map { it.entryTag })
        assertTrue(m1.writeIops >= 3)
    }

    @Test
    fun manifestTag_changesAcrossManifestSequence() {
        val container = VectraDeterministicContainer()
        container.put("vm/layer0/block0", ByteArray(32) { 1 })

        val first = container.buildManifest()
        val second = container.buildManifest()

        assertNotEquals(first.deterministicTag, second.deterministicTag)
        assertTrue(first.entries.first().waveState in 0 until 10)
    }
}
