package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Test

class VectraCrc32CTest {

    @Test
    fun crc32c_knownVector_matchesCastagnoli() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = CRC32C.update(0, data)
        assertEquals(0xE3069283.toInt(), crc)
    }

    @Test
    fun crc32c_chunkedUpdate_matchesSinglePass() {
        val data = "vectras-low-level-determinism".toByteArray(Charsets.UTF_8)
        val single = CRC32C.update(0, data)
        val partA = CRC32C.update(0, data, 0, 10)
        val chunked = CRC32C.update(partA, data, 10, data.size - 10)
        assertEquals(single, chunked)
    }
}
