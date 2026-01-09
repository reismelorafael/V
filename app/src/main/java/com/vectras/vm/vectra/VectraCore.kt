package com.vectras.vm.vectra

import android.content.Context
import android.util.Log
import com.vectras.vm.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

private const val TAG = "VectraCore"

data class VectraState(
    val bitset: LongArray = LongArray(16), // 1024 states
    val stageCounters: LongArray = LongArray(6),
    var crc32c: Int = 0,
    var entropyHint: Int = 0,
    var seed: Int = 0
) {
    fun setFlag(index: Int, enabled: Boolean) {
        if (index !in 0 until 1024) return
        val word = index ushr 6
        val mask = 1L shl (index and 63)
        // branchless toggle using full-bit masks
        val branchless = if (enabled) -1L else 0L
        bitset[word] = (bitset[word] and mask.inv()) or (branchless and mask)
    }
}

object VectraBlock {
    private const val MAGIC = 0x5645435452413031L // "VECTRA01"
    private const val VERSION = 1
    private const val HEADER_BYTES = 64
    private const val CRC_OFFSET = 32
    private const val PRE6_FACTOR = 0x9E3779B97F4A7C15uL.toLong() // 64-bit golden ratio mix
    private const val STRIPE_CFG_DEFAULT = 0x01020304
    private const val ID_PREFIX_DEFAULT = 0x5645435452414C4CL // "VECTRAL"

    fun createHeader(index: Long, payloadLen: Int, seed: Int, stripeCfg: Int = STRIPE_CFG_DEFAULT, idPrefix: Long = ID_PREFIX_DEFAULT): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(MAGIC)
        buffer.putInt(VERSION)
        buffer.putInt(payloadLen)
        buffer.putInt(seed)
        buffer.putLong(index)
        buffer.putInt(0) // crc32c placeholder
        buffer.putInt(stripeCfg)
        buffer.putLong(idPrefix)
        buffer.putLong(seed.toLong() * PRE6_FACTOR) // pre6 derivation
        while (buffer.position() < HEADER_BYTES) buffer.putLong(0)
        val header = buffer.array()
        val crc = CRC32C.update(0, header)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(CRC_OFFSET, crc)
        return header
    }
}

object CRC32C {
    // Castagnoli polynomial
    private const val CRC32C_POLY = 0x82F63B78.toInt()
    private val table: IntArray by lazy { IntArray(256) { i -> calcEntry(i) } }

    private fun calcEntry(i: Int): Int {
        var crc = i
        repeat(8) {
            crc = if ((crc and 1) != 0) (crc ushr 1) xor CRC32C_POLY else crc ushr 1
        }
        return crc
    }

    fun update(initial: Int, data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = initial.inv()
        var idx = offset
        val end = offset + length
        while (idx < end) {
            crc = table[(crc xor data[idx].toInt()) and 0xFF] xor (crc ushr 8)
            idx++
        }
        return crc.inv()
    }
}

object Parity {
    fun stripe(vararg chunks: ByteArray): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        val maxLen = chunks.maxOf { it.size }
        val parity = ByteArray(maxLen)
        for (i in 0 until maxLen) {
            var acc = 0
            for (chunk in chunks) {
                if (i < chunk.size) {
                    acc = acc xor (chunk[i].toInt() and 0xFF)
                }
            }
            parity[i] = acc.toByte()
        }
        return parity
    }
}

class VectraMempool(private val chunkSize: Int, poolSize: Int) {
    companion object {
        private const val MAX_POOL_SIZE = 32
    }

    private val pool = ArrayDeque<ByteArray>()

    init {
        repeat(poolSize) { pool.add(ByteArray(chunkSize)) }
    }

    fun borrow(size: Int = chunkSize): ByteArray {
        return if (size <= chunkSize && pool.isNotEmpty()) {
            pool.removeFirst()
        } else {
            ByteArray(size)
        }
    }

    fun release(buffer: ByteArray) {
        if (buffer.size == chunkSize && pool.size < MAX_POOL_SIZE) {
            pool.addLast(buffer)
        }
    }
}

object VectraCore {
    private const val DEFAULT_CHUNK_BYTES = 64 * 1024
    private const val DEFAULT_POOL_SIZE = 4
    private val state = VectraState()
    private val mempool = VectraMempool(DEFAULT_CHUNK_BYTES, DEFAULT_POOL_SIZE)

    @JvmStatic
    fun init(context: Context) {
        if (!BuildConfig.VECTRA_CORE_ENABLED) return
        state.seed = (System.nanoTime() and 0x7FFFFFFF).toInt()
        val header = VectraBlock.createHeader(index = 0, payloadLen = 0, seed = state.seed)
        state.crc32c = CRC32C.update(0, header)
        state.entropyHint = state.crc32c xor state.seed
        state.stageCounters[0] = 1
        selfTest(header)
        Log.d(TAG, "init seed=${state.seed} crc=${state.crc32c} entropy=${state.entropyHint}")
    }

    private fun selfTest(header: ByteArray) {
        val scratch = mempool.borrow(header.size)
        System.arraycopy(header, 0, scratch, 0, header.size)
        val parity = Parity.stripe(header, scratch)
        val crcAgain = CRC32C.update(0, scratch)
        scratch[0] = (scratch[0].toInt() xor 0xFF).toByte()
        val crcMutated = CRC32C.update(0, scratch)
        val detectsChange = crcMutated != state.crc32c
        val ok = crcAgain == state.crc32c && parity.isNotEmpty() && detectsChange
        state.setFlag(0, ok)
        mempool.release(scratch)
        Log.d(TAG, "selftest_ok=$ok parity_len=${parity.size} crc_mutated=$crcMutated")
    }

    /**
     * PSI stage: fold payload into CRC and advance stage counter (deterministic ingest).
     */
    fun psi(payload: ByteArray): Int {
        val crc = CRC32C.update(state.crc32c, payload)
        state.crc32c = crc
        state.stageCounters[0]++
        return crc
    }

    /**
     * RHO stage: treat noise as data to update entropy hint (no discard of leak/variance).
     */
    fun rho(noise: ByteArray): Int {
        val entropy = CRC32C.update(state.entropyHint, noise)
        state.entropyHint = entropy
        state.stageCounters[1]++
        return entropy
    }

    /**
     * DELTA stage: branchless select between two ints using a mask.
     */
    fun deltaBranchless(a: Int, b: Int, mask: Int): Int {
        val res = (a and mask.inv()) or (b and mask)
        state.stageCounters[2]++
        return res
    }

    /**
     * SIGMA stage: combine two ints with xor and rotate mix (linear pass).
     */
    fun sigmaCombine(a: Int, b: Int): Int {
        val mix = a xor ((b shl 1) or (b ushr 31))
        state.stageCounters[3]++
        return mix
    }

    /**
     * OMEGA stage: finalize digest from crc and entropy hints.
     */
    fun omegaFinalize(): Int {
        state.stageCounters[4]++
        return state.crc32c xor state.entropyHint
    }
}
