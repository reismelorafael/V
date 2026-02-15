package com.vectras.vm.vectra

import android.content.Context
import android.os.Build
import android.util.Log
import com.vectras.vm.BuildConfig
import com.vectras.vm.core.NativeFastPath
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "VectraCore"
private val EMPTY_PAYLOAD = ByteArray(0)

private object VectraLogClock {
    private val logTick = AtomicLong(0)

    fun nextTick(): Long = logTick.incrementAndGet()
}

private fun logDebug(message: String, deterministicTick: Long = VectraLogClock.nextTick(), wallClockMs: Long = System.currentTimeMillis()) {
    Log.d(TAG, "deterministic_tick=$deterministicTick wall_clock_ms=$wallClockMs $message")
}

private fun logWarn(message: String, deterministicTick: Long = VectraLogClock.nextTick(), wallClockMs: Long = System.currentTimeMillis()) {
    Log.w(TAG, "deterministic_tick=$deterministicTick wall_clock_ms=$wallClockMs $message")
}

private fun logError(message: String, throwable: Throwable? = null, deterministicTick: Long = VectraLogClock.nextTick(), wallClockMs: Long = System.currentTimeMillis()) {
    Log.e(TAG, "deterministic_tick=$deterministicTick wall_clock_ms=$wallClockMs $message", throwable)
}

/**
 * VectraState: Holds 1024-bit flags (state depth/stack flags).
 * Uses BitSet-like structure with LongArray for efficient bit operations.
 */
data class VectraState(
    val bitset: LongArray = LongArray(16), // 1024 states = 16 * 64 bits
    val stageCounters: LongArray = LongArray(6),
    var crc32c: Int = 0,
    var entropyHint: Int = 0,
    var seed: Int = 0,
    var hitStreak: Int = 0,
    var missStreak: Int = 0,
    var policyMode: Int = 0
) {
    fun setFlag(index: Int, enabled: Boolean) {
        if (index !in 0 until 1024) return
        val word = index ushr 6
        val mask = 1L shl (index and 63)
        // branchless toggle using full-bit masks
        val branchless = if (enabled) -1L else 0L
        bitset[word] = (bitset[word] and mask.inv()) or (branchless and mask)
    }

    fun getFlag(index: Int): Boolean {
        if (index !in 0 until 1024) return false
        val word = index ushr 6
        val mask = 1L shl (index and 63)
        return (bitset[word] and mask) != 0L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectraState
        if (!bitset.contentEquals(other.bitset)) return false
        if (!stageCounters.contentEquals(other.stageCounters)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bitset.contentHashCode()
        result = 31 * result + stageCounters.contentHashCode()
        return result
    }
}

/**
 * VectraBlock: Represents one 4x4 block = 16 bits + parity8 + crc32c + metadata.
 * Base cell: 4x4 = 16 (area); "8" is parity (4 row + 4 col).
 */
object VectraBlock {
    private const val MAGIC = 0x5645435452413031L // "VECTRA01"
    private const val VERSION = 1
    private const val HEADER_BYTES = 64
    private const val CRC_OFFSET = 32
    private val PRE6_FACTOR = 0x9E3779B97F4A7C15uL.toLong() // 64-bit golden ratio mix
    private const val STRIPE_CFG_DEFAULT = 0x01020304
    private const val ID_PREFIX_DEFAULT = 0x5645435452414C4CL // "VECTRAL"

    /**
     * Creates a 4x4 block with 16 bits of data.
     * Returns packed data: upper 16 bits = data, lower 8 bits = parity8
     */
    fun create4x4Block(data16: Int): Int {
        val parity8 = Parity.parity2D8(data16)
        return (data16 shl 8) or (parity8 and 0xFF)
    }

    /**
     * Extracts 16-bit data from packed block
     */
    fun extractData(packed: Int): Int = (packed ushr 8) and 0xFFFF

    /**
     * Extracts 8-bit parity from packed block
     */
    fun extractParity(packed: Int): Int = packed and 0xFF

    /**
     * Verifies block integrity by recomputing parity
     */
    fun verify4x4Block(packed: Int): Boolean {
        val data = extractData(packed)
        val storedParity = extractParity(packed)
        val computedParity = Parity.parity2D8(data)
        return storedParity == computedParity
    }

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

/**
 * CRC32C: Castagnoli polynomial for data integrity.
 * Software implementation (no native required for MVP).
 */
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

/**
 * Parity: Helper for 2D parity computation on 4x4 blocks.
 * 4 row parity + 4 col parity => 8 bits total.
 * idx mapping: idx=(y<<2)|x for 4x4 grid
 */
object Parity {
    /**
     * Computes 2D parity for a 4x4 block (16 bits).
     * Returns 8 bits: [row3, row2, row1, row0, col3, col2, col1, col0]
     */
    fun parity2D8(data16: Int): Int {
        var parity = 0
        // Compute row parities (bits 4-7)
        for (row in 0..3) {
            var rowParity = 0
            for (col in 0..3) {
                val idx = (row shl 2) or col
                val bit = (data16 ushr idx) and 1
                rowParity = rowParity xor bit
            }
            parity = parity or (rowParity shl (row + 4))
        }
        // Compute column parities (bits 0-3)
        for (col in 0..3) {
            var colParity = 0
            for (row in 0..3) {
                val idx = (row shl 2) or col
                val bit = (data16 ushr idx) and 1
                colParity = colParity xor bit
            }
            parity = parity or (colParity shl col)
        }
        return parity
    }

    /**
     * Computes syndrome (difference) between stored and computed parity.
     * Returns popcount of XOR difference.
     */
    fun syndrome(storedParity8: Int, computedParity8: Int): Int {
        val diff = storedParity8 xor computedParity8
        return Integer.bitCount(diff)
    }

    /**
     * Stripe parity across multiple chunks (for redundancy).
     */
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

/**
 * VectraMemPool: Fixed-size memory pool to avoid GC churn.
 * Manages reusable byte buffers.
 */
class VectraMemPool(private val chunkSize: Int, poolSize: Int) {
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

/**
 * VectraEvent: Represents an IRQ-like priority event.
 * "Finger" = IRQ-like priority request (4G/radio is major source).
 */
class VectraEvent(
    var type: EventType,
    var priority: Int, // Higher = more urgent
    var timestamp: Long = System.nanoTime(),
    var payload: ByteArray? = null,
    internal var payloadLength: Int = payload?.size ?: 0,
    internal var slotIndex: Int = -1
) : Comparable<VectraEvent> {
    companion object {
        private val deterministicCounter = AtomicLong(0)

        fun nextDeterministicTick(): Long = deterministicCounter.incrementAndGet()
    }

    enum class EventType {
        TIMER_TICK,
        NETWORK_CHANGE,
        RADIO_EVENT,
        USER_INPUT,
        SYSTEM_EVENT
    }

    override fun compareTo(other: VectraEvent): Int {
        // Higher priority first, then older deterministic tick first
        val priorityDiff = other.priority - this.priority
        return if (priorityDiff != 0) priorityDiff else deterministicTick.compareTo(other.deterministicTick)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectraEvent
        if (type != other.type) return false
        if (priority != other.priority) return false
        if (deterministicTick != other.deterministicTick) return false
        if (payload != null) {
            if (other.payload == null) return false
            if (!payload.contentEquals(other.payload)) return false
        } else if (other.payload != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + priority
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + payloadLength
        return result
    }
}

/**
 * VectraFlowRoute: deterministic routing for CPU/Storage/Input/Output pressure.
 */
enum class VectraFlowRoute {
    CPU_HEAVY,
    STORAGE_HEAVY,
    IO_HEAVY,
    BALANCED
}

/**
 * VectraFlowSnapshot: output from one orchestration step.
 */
data class VectraFlowSnapshot(
    val orchestrationTag: Long,
    val route: VectraFlowRoute,
    val cpuPressure: Int,
    val storagePressure: Int,
    val ioPressure: Int,
    val matrixDeterminant: Long
)

/**
 * VectraDataOrchestrator: deterministic pipeline for CPU/Storage/Input/Output flow calculations.
 *
 * Pipeline stages:
 * 1) Input: normalize signals and collect counters
 * 2) Process: compute pressure vectors and matrix determinant
 * 3) Output: generate route and orchestration tag
 * 4) Next: persist values in ring buffer for temporal continuity
 */
class VectraDataOrchestrator(private val state: VectraState) {
    private companion object {
        private const val HISTORY_SIZE = 128
        private const val FLOW_MIX_A = -0x61c8864680b583ebL
        private const val FLOW_MIX_B = -0x4498517a7b3558c5L
        private const val FLOW_MIX_C = -7046029254386353131L
    }

    private val history = LongArray(HISTORY_SIZE)
    private var historyIndex = 0
    private var sequence = 0L
    private var lastRoute: VectraFlowRoute = VectraFlowRoute.BALANCED

    fun orchestrate(
        cpuCycles: Long,
        storageReadBytes: Long,
        storageWriteBytes: Long,
        inputBytes: Long,
        outputBytes: Long,
        m00: Long,
        m01: Long,
        m10: Long,
        m11: Long
    ): VectraFlowSnapshot {
        val cpu = if (cpuCycles < 0) 0 else cpuCycles
        val storageRead = if (storageReadBytes < 0) 0 else storageReadBytes
        val storageWrite = if (storageWriteBytes < 0) 0 else storageWriteBytes
        val input = if (inputBytes < 0) 0 else inputBytes
        val output = if (outputBytes < 0) 0 else outputBytes

        val storageTotal = safeAdd(storageRead, storageWrite)
        val ioDiff = if (input >= output) input - output else output - input
        val ioVolume = safeAdd(input, output)
        val matrixDet = safeSub(safeMul(m00, m11), safeMul(m01, m10))

        val cpuPressure = log2p1(cpu)
        val storagePressure = log2p1(storageTotal)
        val ioPressure = log2p1(ioVolume)

        val margin = 1
        val routeCandidate = chooseRoute(cpuPressure, storagePressure, ioPressure, margin)
        val route = stabilizeRoute(lastRoute, routeCandidate, cpuPressure, storagePressure, ioPressure, margin)
        lastRoute = route

        val mixed = mix64(
            cpu xor (storageTotal shl 1) xor (ioDiff shl 2) xor matrixDet xor sequence
        )
        val routeTagged = mixed xor route.ordinal.toLong()

        pushHistory(cpu)
        pushHistory(storageRead)
        pushHistory(storageWrite)
        pushHistory(input)
        pushHistory(output)
        pushHistory(routeTagged)
        sequence++

        state.stageCounters[0] += input
        state.stageCounters[1] += cpu
        state.stageCounters[2] += storageTotal
        state.stageCounters[3] += output
        state.stageCounters[4] = routeTagged

        return VectraFlowSnapshot(
            orchestrationTag = routeTagged,
            route = route,
            cpuPressure = cpuPressure,
            storagePressure = storagePressure,
            ioPressure = ioPressure,
            matrixDeterminant = matrixDet
        )
    }

    private fun pushHistory(value: Long) {
        history[historyIndex] = value
        historyIndex = (historyIndex + 1) and (HISTORY_SIZE - 1)
    }

    private fun mix64(value: Long): Long {
        var x = value + FLOW_MIX_C
        x = (x xor (x ushr 30)) * FLOW_MIX_A
        x = (x xor (x ushr 27)) * FLOW_MIX_B
        return x xor (x ushr 31)
    }

    private fun log2p1(x: Long): Int {
        val v = if (x <= 0L) 0L else x
        return 63 - java.lang.Long.numberOfLeadingZeros(v + 1L)
    }

    private fun chooseRoute(cpuP: Int, storP: Int, ioP: Int, margin: Int): VectraFlowRoute {
        return when {
            cpuP >= storP + margin && cpuP >= ioP + margin -> VectraFlowRoute.CPU_HEAVY
            storP >= cpuP + margin && storP >= ioP + margin -> VectraFlowRoute.STORAGE_HEAVY
            ioP >= cpuP + margin && ioP >= storP + margin -> VectraFlowRoute.IO_HEAVY
            else -> VectraFlowRoute.BALANCED
        }
    }

    private fun stabilizeRoute(
        prev: VectraFlowRoute,
        cand: VectraFlowRoute,
        cpuP: Int,
        storP: Int,
        ioP: Int,
        margin: Int
    ): VectraFlowRoute {
        if (cand == prev) return prev
        if (cand == VectraFlowRoute.BALANCED) return prev
        return when (cand) {
            VectraFlowRoute.CPU_HEAVY -> if (cpuP >= storP + margin && cpuP >= ioP + margin) cand else prev
            VectraFlowRoute.STORAGE_HEAVY -> if (storP >= cpuP + margin && storP >= ioP + margin) cand else prev
            VectraFlowRoute.IO_HEAVY -> if (ioP >= cpuP + margin && ioP >= storP + margin) cand else prev
            VectraFlowRoute.BALANCED -> prev
        }
    }

    private fun safeAdd(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            if (a >= 0 && b >= 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    private fun safeMul(a: Long, b: Long): Long {
        return try {
            Math.multiplyExact(a, b)
        } catch (_: ArithmeticException) {
            if ((a xor b) < 0) Long.MIN_VALUE else Long.MAX_VALUE
        }
    }

    private fun safeSub(a: Long, b: Long): Long {
        return try {
            Math.subtractExact(a, b)
        } catch (_: ArithmeticException) {
            if (a >= 0 && b < 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }
}

/**
 * VectraEventBus: Priority queue for IRQ-like events.
 * Thread-safe event bus with priority handling.
 */
class VectraEventBus {
    companion object {
        private const val EVENT_RING_CAPACITY = 256
        private const val EVENT_SLOT_COUNT = 256
        private const val EVENT_PAYLOAD_CAPACITY = 512
        private const val NATIVE_ARENA_BYTES = EVENT_SLOT_COUNT * EVENT_PAYLOAD_CAPACITY
        private const val NATIVE_TIMER_STAGING_OFFSET = NATIVE_ARENA_BYTES
    }

    private val queueRing = IntArray(EVENT_RING_CAPACITY)
    private val freeSlots = IntArray(EVENT_SLOT_COUNT) { idx -> EVENT_SLOT_COUNT - 1 - idx }
    private val payloadArena = Array(EVENT_SLOT_COUNT) { ByteArray(EVENT_PAYLOAD_CAPACITY) }
    private val eventPool = Array(EVENT_SLOT_COUNT) { idx ->
        VectraEvent(
            type = VectraEvent.EventType.SYSTEM_EVENT,
            priority = 0,
            timestamp = 0L,
            payload = payloadArena[idx],
            payloadLength = 0,
            slotIndex = idx
        )
    }
    private val nativeArenaHandle = NativeFastPath.allocArena(NATIVE_ARENA_BYTES + EVENT_PAYLOAD_CAPACITY)
    private val lock = ReentrantLock()
    private var queueHead = 0
    private var queueTail = 0
    private var queueSize = 0
    private var freeTop = EVENT_SLOT_COUNT

    private fun acquireSlotLocked(): Int {
        return if (freeTop == 0) -1 else freeSlots[--freeTop]
    }

    private fun releaseSlotLocked(slot: Int) {
        if (slot !in 0 until EVENT_SLOT_COUNT || freeTop >= EVENT_SLOT_COUNT) return
        freeSlots[freeTop++] = slot
    }

    private fun writePayloadToSlot(slot: Int, source: ByteArray?, sourceLength: Int) {
        val slotBuffer = payloadArena[slot]
        if (source == null || sourceLength <= 0) {
            eventPool[slot].payloadLength = 0
            return
        }
        val len = sourceLength.coerceAtMost(EVENT_PAYLOAD_CAPACITY).coerceAtMost(source.size)
        System.arraycopy(source, 0, slotBuffer, 0, len)
        eventPool[slot].payloadLength = len
        if (nativeArenaHandle > 0) {
            NativeFastPath.writeArena(
                nativeArenaHandle,
                slot * EVENT_PAYLOAD_CAPACITY,
                slotBuffer,
                0,
                len
            )
        }
    }

    private fun writeTimerPayloadToSlot(slot: Int, timestampMillis: Long) {
        val slotBuffer = payloadArena[slot]
        slotBuffer[0] = (timestampMillis and 0xFF).toByte()
        slotBuffer[1] = ((timestampMillis ushr 8) and 0xFF).toByte()
        slotBuffer[2] = ((timestampMillis ushr 16) and 0xFF).toByte()
        slotBuffer[3] = ((timestampMillis ushr 24) and 0xFF).toByte()
        slotBuffer[4] = ((timestampMillis ushr 32) and 0xFF).toByte()
        slotBuffer[5] = ((timestampMillis ushr 40) and 0xFF).toByte()
        slotBuffer[6] = ((timestampMillis ushr 48) and 0xFF).toByte()
        slotBuffer[7] = ((timestampMillis ushr 56) and 0xFF).toByte()
        eventPool[slot].payloadLength = 8
        if (nativeArenaHandle > 0) {
            val wroteStaging = NativeFastPath.writeArena(nativeArenaHandle, NATIVE_TIMER_STAGING_OFFSET, slotBuffer, 0, 8)
            val copied = wroteStaging && NativeFastPath.copyArena(
                nativeArenaHandle,
                NATIVE_TIMER_STAGING_OFFSET,
                nativeArenaHandle,
                slot * EVENT_PAYLOAD_CAPACITY,
                8
            )
            if (!copied) {
                NativeFastPath.writeArena(nativeArenaHandle, slot * EVENT_PAYLOAD_CAPACITY, slotBuffer, 0, 8)
            }
        }
    }

    fun post(event: VectraEvent) {
        val payload = event.payload
        postInternal(
            type = event.type,
            priority = event.priority,
            timestamp = event.timestamp,
            payload = payload,
            payloadLength = event.payloadLength.takeIf { it > 0 } ?: (payload?.size ?: 0)
        )
    }

    fun postTimerTick(nowMs: Long) {
        val payload = timerPayloadBuffer.get()
        writeLongLe(payload, 0, nowMs)
        postInternal(
            type = VectraEvent.EventType.TIMER_TICK,
            priority = 1,
            timestamp = System.nanoTime(),
            payload = payload,
            payloadLength = TIMER_PAYLOAD_BYTES
        )
    }

    private fun postInternal(type: VectraEvent.EventType, priority: Int, timestamp: Long, payload: ByteArray?, payloadLength: Int) {
        lock.withLock {
            if (queueSize >= EVENT_RING_CAPACITY) return
            val slot = acquireSlotLocked()
            if (slot < 0) return
            val pooledEvent = eventPool[slot]
            pooledEvent.type = event.type
            pooledEvent.priority = event.priority
            pooledEvent.timestamp = event.timestamp
            writePayloadToSlot(slot, event.payload, event.payloadLength)

            queueRing[queueTail] = slot
            queueTail = (queueTail + 1) and (EVENT_RING_CAPACITY - 1)
            queueSize++
        }
    }

    fun postTimerTick(timestampMillis: Long) {
        lock.withLock {
            if (queueSize >= EVENT_RING_CAPACITY) return
            val slot = acquireSlotLocked()
            if (slot < 0) return
            val pooledEvent = eventPool[slot]
            pooledEvent.type = VectraEvent.EventType.TIMER_TICK
            pooledEvent.priority = 1
            pooledEvent.timestamp = System.nanoTime()
            writeTimerPayloadToSlot(slot, timestampMillis)
            queueRing[queueTail] = slot
            queueTail = (queueTail + 1) and (EVENT_RING_CAPACITY - 1)
            queueSize++
        }
    }

    fun poll(): VectraEvent? {
        lock.withLock {
            if (queueSize == 0) return null
            var bestPos = queueHead
            var bestSlot = queueRing[queueHead]
            var cursor = queueHead
            repeat(queueSize) {
                val slot = queueRing[cursor]
                if (eventPool[slot].compareTo(eventPool[bestSlot]) < 0) {
                    bestSlot = slot
                    bestPos = cursor
                }
                cursor = (cursor + 1) and (EVENT_RING_CAPACITY - 1)
            }

            var pos = bestPos
            while (pos != queueTail) {
                val next = (pos + 1) and (EVENT_RING_CAPACITY - 1)
                if (next == queueTail) break
                queueRing[pos] = queueRing[next]
                pos = next
            }
            queueTail = (queueTail - 1) and (EVENT_RING_CAPACITY - 1)
            queueSize--
            return eventPool[bestSlot]
        }
    }

    fun recycle(event: VectraEvent?) {
        if (event == null) return
        lock.withLock {
            val slot = event.slotIndex
            if (slot !in 0 until EVENT_SLOT_COUNT) return
            event.payloadLength = 0
            releaseSlotLocked(slot)
        }
    }

    fun close() {
        if (nativeArenaHandle > 0) {
            NativeFastPath.freeArena(nativeArenaHandle)
        }
    }

    fun size(): Int {
        lock.withLock {
            return queueSize
        }
    }

    fun clear() {
        lock.withLock {
            queueHead = 0
            queueTail = 0
            queueSize = 0
            freeTop = 0
            for (idx in 0 until EVENT_SLOT_COUNT) {
                freeSlots[freeTop++] = EVENT_SLOT_COUNT - 1 - idx
                eventPool[idx].payloadLength = 0
            }
        }
    }

    fun close() {
        clear()
        if (nativeArenaHandle > 0) {
            NativeFastPath.freeArena(nativeArenaHandle)
        }
        if (mirrorArenaHandle > 0) {
            NativeFastPath.freeArena(mirrorArenaHandle)
        }
    }

    private fun writeLongLe(dst: ByteArray, offset: Int, value: Long) {
        dst[offset] = value.toByte()
        dst[offset + 1] = (value ushr 8).toByte()
        dst[offset + 2] = (value ushr 16).toByte()
        dst[offset + 3] = (value ushr 24).toByte()
        dst[offset + 4] = (value ushr 32).toByte()
        dst[offset + 5] = (value ushr 40).toByte()
        dst[offset + 6] = (value ushr 48).toByte()
        dst[offset + 7] = (value ushr 56).toByte()
    }

    private val timerPayloadBuffer = ThreadLocal.withInitial { ByteArray(TIMER_PAYLOAD_BYTES) }
}

/**
 * VectraCycle: Implements 4-phase loop (Input → Process → Output → Next).
 * Deterministic cycle for event processing.
 */
class VectraCycle(
    private val eventBus: VectraEventBus,
    private val state: VectraState,
    private val logger: VectraBitStackLog?
) {
    private val running = AtomicBoolean(false)
    private var cycleThread: Thread? = null
    private var cycleCount = 0L

    fun start() {
        if (running.getAndSet(true)) return
        cycleThread = Thread {
            logDebug("VectraCycle started")
            while (running.get()) {
                try {
                    executeCycle()
                    Thread.sleep(100) // 10 Hz cycle rate
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logError("Cycle error", e)
                }
            }
            logDebug("VectraCycle stopped")
        }.apply {
            name = "VectraCycle"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        cycleThread?.interrupt()
        cycleThread = null
    }

    /**
     * 4-phase cycle:
     * 1. Input: Poll event from bus
     * 2. Process: Update state based on event
     * 3. Output: Log state change
     * 4. Next: Prepare for next cycle
     */
    private fun executeCycle() {
        // Phase 1: Input
        val event = eventBus.poll()
        val inputBytes = event?.payloadLength?.toLong() ?: 0L
        state.stageCounters[0] += inputBytes
        updatePolicy(event)

        // Phase 2: Process
        if (event != null) {
            processEvent(event)
        }

        // Phase 3: Output
        logger?.let {
            val payload = event?.payload ?: EMPTY_PAYLOAD
            val meta = (event?.type?.ordinal ?: 0) or ((event?.priority ?: 0) shl 8)
            it.append(payload, event?.payloadLength ?: 0, meta)
            state.stageCounters[3] += (event?.payloadLength ?: 0).toLong()
        }

        // Phase 4: Next
        cycleCount++
        state.stageCounters[5] = cycleCount // Track total cycles
        eventBus.recycle(event)
    }

    private fun updatePolicy(event: VectraEvent?) {
        if (event == null) {
            state.missStreak++
            state.hitStreak = 0
        } else {
            state.hitStreak++
            state.missStreak = 0
        }

        if (state.missStreak >= 2) {
            state.policyMode = 1
        } else if (state.hitStreak >= 2) {
            state.policyMode = 0
        }
    }

    private fun processEvent(event: VectraEvent) {
        // Update entropy hint based on event type
        val baseWeight = when (event.type) {
            VectraEvent.EventType.RADIO_EVENT -> 10 // Radio events add more rho
            VectraEvent.EventType.NETWORK_CHANGE -> 5
            VectraEvent.EventType.TIMER_TICK -> 1
            else -> 2
        }
        val weight = if (state.policyMode == 1) {
            (baseWeight ushr 1).coerceAtLeast(1)
        } else {
            baseWeight
        }
        
        if (event.payload != null && event.payloadLength > 0) {
            val entropy = CRC32C.update(state.entropyHint, event.payload!!, 0, event.payloadLength)
            state.entropyHint = entropy + weight
            state.seed = state.seed xor entropy
            state.stageCounters[1] += weight.toLong()
        }

        // Update flag to indicate event processed
        val flagIndex = event.type.ordinal
        state.setFlag(flagIndex, true)
    }
}

/**
 * VectraTriad: CPU/RAM/DISK states with 2-of-3 consensus.
 * Physical model for detecting which component is "out".
 */
data class VectraTriad(
    var cpuState: Int = 0,
    var ramState: Int = 0,
    var diskState: Int = 0
) {
    enum class Component {
        CPU, RAM, DISK, NONE
    }

    /**
     * 2-of-3 consensus: If two agree, the third is "out".
     * Returns which component is out-of-sync.
     */
    fun whoOut(): Component {
        return when {
            cpuState == ramState && cpuState != diskState -> Component.DISK
            cpuState == diskState && cpuState != ramState -> Component.RAM
            ramState == diskState && ramState != cpuState -> Component.CPU
            else -> Component.NONE // All agree or all differ
        }
    }

    /**
     * Updates one component and returns if consensus changed.
     */
    fun update(component: Component, newState: Int): Boolean {
        val oldOut = whoOut()
        when (component) {
            Component.CPU -> cpuState = newState
            Component.RAM -> ramState = newState
            Component.DISK -> diskState = newState
            Component.NONE -> {}
        }
        val newOut = whoOut()
        return oldOut != newOut
    }
}

/**
 * VectraBitStackLog: Append-only file logger with binary format.
 * Deterministic evidence log (BitStack style) using CRC32C.
 */
class VectraBitStackLog(logFile: File) {
    companion object {
        private const val MAGIC = 0x56454354L // "VECT"
        private const val VERSION = 1
        private const val RECORD_HEADER_SIZE = 32 // magic(4) + len(4) + meta(4) + tick(8) + wall(8) + crc(4) + reserved(4)
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10 MB
        private const val FLUSH_INTERVAL_MS = 1000L
        private const val FLUSH_RECORDS = 32
    }

    private val file: RandomAccessFile = RandomAccessFile(logFile, "rw")
    private val lock = ReentrantLock()
    private val appendHeaderBuffer = ThreadLocal.withInitial { ByteArray(RECORD_HEADER_SIZE) }
    private var recordCount = 0L
    private var recordsSinceFlush = 0
    private var lastFlushAtMs = System.currentTimeMillis()

    init {
        if (file.length() == 0L) {
            writeHeader()
        }
    }

    private fun writeHeader() {
        lock.withLock {
            val header = ByteArray(16)
            writeIntLE(header, 0, MAGIC.toInt())
            writeIntLE(header, 4, VERSION)
            writeLongLE(header, 8, System.currentTimeMillis())
            file.write(header)
            file.channel.force(true)
        }
    }

    /**
     * Appends a record: [u32 magic, u32 len, u32 meta, i64 deterministic_tick, i64 wall_clock_ms, u32 crc, u32 reserved, payload]
     */
    fun append(payload: ByteArray, payloadLength: Int = payload.size, meta: Int = 0) {
        lock.withLock {
            if (file.length() >= MAX_LOG_SIZE) {
                logWarn("Log size exceeded, skipping append")
                return
            }

            val len = payloadLength.coerceIn(0, payload.size)

            val recordHeader = appendHeaderBuffer.get()
            writeIntLE(recordHeader, 0, MAGIC.toInt())
            writeIntLE(recordHeader, 4, len)
            writeIntLE(recordHeader, 8, meta)

            // Compute CRC incrementally without concatenation
            var crc = CRC32C.update(0, recordHeader, 0, 12)
            crc = CRC32C.update(crc, payload, 0, len)
            writeIntLE(recordHeader, 12, crc)

            file.write(recordHeader, 0, RECORD_HEADER_SIZE)
            file.write(payload, 0, len)
            recordsSinceFlush++
            maybeFlush()
            recordCount++
        }
    }

    private fun writeIntLE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeLongLE(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 4] = ((value ushr 32) and 0xFF).toByte()
        buffer[offset + 5] = ((value ushr 40) and 0xFF).toByte()
        buffer[offset + 6] = ((value ushr 48) and 0xFF).toByte()
        buffer[offset + 7] = ((value ushr 56) and 0xFF).toByte()
    }

    private fun maybeFlush() {
        val now = System.currentTimeMillis()
        if (recordsSinceFlush >= FLUSH_RECORDS || now - lastFlushAtMs >= FLUSH_INTERVAL_MS) {
            file.channel.force(false)
            recordsSinceFlush = 0
            lastFlushAtMs = now
        }
    }

    fun close() {
        lock.withLock {
            if (recordsSinceFlush > 0) {
                file.channel.force(false)
                recordsSinceFlush = 0
            }
            file.close()
        }
    }

    private fun putIntLe(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = value.toByte()
        dst[offset + 1] = (value ushr 8).toByte()
        dst[offset + 2] = (value ushr 16).toByte()
        dst[offset + 3] = (value ushr 24).toByte()
    }

    private val recordHeaderBuffer = ThreadLocal.withInitial { ByteArray(RECORD_HEADER_SIZE) }

    fun getRecordCount(): Long = recordCount
    fun getFileSize(): Long = file.length()
}

/**
 * VectraCore: Main initialization and coordination.
 * Gated behind BuildConfig.VECTRA_CORE_ENABLED.
 */
object VectraCore {
    private const val DEFAULT_CHUNK_BYTES = 64 * 1024
    private const val DEFAULT_POOL_SIZE = 4
    private const val VECTRA_DEFAULT_SEED = 0x13579BDF
    private val state = VectraState()
    private val triad = VectraTriad()
    private val mempool = VectraMemPool(DEFAULT_CHUNK_BYTES, DEFAULT_POOL_SIZE)
    private val flowOrchestrator = VectraDataOrchestrator(state)
    private val deterministicContainer = VectraDeterministicContainer(state = state)
    private var eventBus: VectraEventBus? = null
    private var cycle: VectraCycle? = null
    private var logger: VectraBitStackLog? = null
    private val initialized = AtomicBoolean(false)
    private val timerTickCounter = java.util.concurrent.atomic.AtomicLong(0L)

    @JvmOverloads
    @JvmStatic
    fun init(context: Context, configuredSeed: Int? = null) {
        if (!BuildConfig.VECTRA_CORE_ENABLED) {
            logDebug("VectraCore disabled by BuildConfig")
            return
        }

        if (initialized.getAndSet(true)) {
            logDebug("VectraCore already initialized")
            return
        }

        state.seed = resolveConfiguredSeed(context)
        val header = VectraBlock.createHeader(index = 0, payloadLen = 0, seed = state.seed)
        state.crc32c = CRC32C.update(0, header)
        state.entropyHint = state.crc32c xor state.seed
        state.stageCounters[0] = 1

        // Initialize logger
        val logFile = File(context.filesDir, "vectra_core.log")
        logger = VectraBitStackLog(logFile)

        // Initialize event bus and cycle
        eventBus = VectraEventBus()
        cycle = VectraCycle(eventBus!!, state, logger)
        cycle?.start()

        // Run self-test
        selfTest(header)

        // Start timer tick events
        startTimerTicks()

        logDebug("init seed=${state.seed} crc=${state.crc32c} entropy=${state.entropyHint} logPath=${logFile.absolutePath}")
    }

    private fun resolveConfiguredSeed(context: Context): Int {
        val configuredSeed = BuildConfig.VECTRA_CORE_SEED
        if (configuredSeed != 0) {
            return configuredSeed and Int.MAX_VALUE
        }

        val fingerprint = buildString {
            append(context.packageName)
            append('|')
            append(BuildConfig.VERSION_NAME)
            append('|')
            append(Build.BRAND)
            append('|')
            append(Build.DEVICE)
            append('|')
            append(Build.HARDWARE)
            append('|')
            append(Build.FINGERPRINT)
            append('|')
            append(Build.SUPPORTED_ABIS.joinToString(","))
        }
        val bytes = fingerprint.toByteArray(Charsets.UTF_8)
        val derived = CRC32C.update(VECTRA_DEFAULT_SEED, bytes) and Int.MAX_VALUE
        return if (derived == 0) VECTRA_DEFAULT_SEED else derived
    }

    /**
     * Self-test: Creates a block, mutates a copy, detects changes.
     * Validates CRC and parity detection (1-bit-safe ethos).
     */
    private fun selfTest(header: ByteArray) {
        logDebug("Running self-test...")
        
        // Test 1: Header CRC verification
        val scratch = mempool.borrow(header.size)
        System.arraycopy(header, 0, scratch, 0, header.size)
        val crcAgain = CRC32C.update(0, scratch)
        val headerOk = crcAgain == state.crc32c
        
        // Test 2: Bit flip detection
        scratch[0] = (scratch[0].toInt() xor 0xFF).toByte()
        val crcMutated = CRC32C.update(0, scratch)
        val detectsChange = crcMutated != state.crc32c
        
        // Test 3: 4x4 block parity
        val testData = 0b1010101010101010 // Alternating pattern
        val packed = VectraBlock.create4x4Block(testData)
        val parityOk = VectraBlock.verify4x4Block(packed)
        
        // Test 4: Bit flip in 4x4 block
        val flippedData = testData xor 1 // Flip one bit
        val flippedPacked = (flippedData shl 8) or VectraBlock.extractParity(packed)
        val detectsBitFlip = !VectraBlock.verify4x4Block(flippedPacked)
        
        // Test 5: Parity syndrome
        val storedParity = Parity.parity2D8(testData)
        val computedParity = Parity.parity2D8(flippedData)
        val syndrome = Parity.syndrome(storedParity, computedParity)
        val syndromeOk = syndrome > 0
        
        mempool.release(scratch)
        
        val allOk = headerOk && detectsChange && parityOk && detectsBitFlip && syndromeOk
        state.setFlag(0, allOk)
        
        // Log self-test results
        val result = ByteBuffer.allocate(20)
        result.put(if (headerOk) 1.toByte() else 0.toByte())
        result.put(if (detectsChange) 1.toByte() else 0.toByte())
        result.put(if (parityOk) 1.toByte() else 0.toByte())
        result.put(if (detectsBitFlip) 1.toByte() else 0.toByte())
        result.put(if (syndromeOk) 1.toByte() else 0.toByte())
        result.putInt(crcMutated)
        result.putInt(syndrome)
        result.putInt(packed)
        logger?.append(result.array(), meta = 0xFFFF) // meta=0xFFFF for self-test
        
        logDebug("selftest_ok=$allOk headerOk=$headerOk detectsChange=$detectsChange parityOk=$parityOk detectsBitFlip=$detectsBitFlip syndromeOk=$syndromeOk syndrome=$syndrome")
    }

    /**
     * Starts a background thread that posts timer tick events.
     */
    private fun startTimerTicks() {
        Thread {
            while (initialized.get()) {
                try {
                    eventBus?.postTimerTick(System.currentTimeMillis())
                    Thread.sleep(1000) // 1 Hz tick
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logError("Timer tick error", e)
                }
            }
        }.apply {
            name = "VectraTimerTick"
            isDaemon = true
            start()
        }
    }


    private fun resolveSeed(context: Context, configuredSeed: Int?): Int {
        if (configuredSeed != null) {
            return configuredSeed and 0x7FFFFFFF
        }

        val stableFingerprint = buildString {
            append(context.packageName)
            append('|').append(BuildConfig.VERSION_NAME)
            append('|').append(BuildConfig.VERSION_CODE)
            append('|').append(Build.BOARD)
            append('|').append(Build.BRAND)
            append('|').append(Build.DEVICE)
            append('|').append(Build.HARDWARE)
            append('|').append(Build.PRODUCT)
            append('|').append(Build.FINGERPRINT)
            append('|').append(Build.SUPPORTED_ABIS.joinToString(","))
        }
        return CRC32C.update(0, stableFingerprint.toByteArray(Charsets.UTF_8)) and 0x7FFFFFFF
    }

    /**
     * Shutdown hook for cleanup.
     */
    @JvmStatic
    fun shutdown() {
        if (!initialized.getAndSet(false)) return
        cycle?.stop()
        logger?.close()
        eventBus?.clear()
        eventBus?.close()
        Log.d(TAG, "VectraCore shutdown complete")
    }

    /**
     * PSI stage: fold payload into CRC and advance stage counter (deterministic ingest).
     * Noise is data (ρ = information not decoded yet).
     */
    fun psi(payload: ByteArray): Int {
        val crc = if (NativeFastPath.isNativeAvailable()) {
            NativeFastPath.ingest(payload)
        } else {
            CRC32C.update(state.crc32c, payload)
        }
        state.crc32c = crc
        state.stageCounters[0]++
        return crc
    }

    /**
     * RHO stage: treat noise as data to update entropy hint.
     * Do not treat noise as simple bug - it's information not decoded yet.
     */
    fun rho(noise: ByteArray, eventWeight: Int = 1): Int {
        val entropy = NativeFastPath.coreIngest(noise)
        if (entropy != Int.MIN_VALUE) {
            state.entropyHint = entropy + eventWeight
            state.stageCounters[1]++
            return entropy
        }
        return state.entropyHint
    }

    /**
     * DELTA stage: branchless select between two ints using a mask.
     */
    fun deltaBranchless(a: Int, b: Int, mask: Int): Int {
        val res = NativeFastPath.coreProcess(a, b, mask)
        if (res != Int.MIN_VALUE) {
            state.stageCounters[2]++
            return res
        }
        return 0
    }

    /**
     * SIGMA stage: combine two ints with xor and rotate mix (linear pass).
     */
    fun sigmaCombine(a: Int, b: Int): Int {
        val mix = NativeFastPath.coreProcess(a, b, 1)
        if (mix != Int.MIN_VALUE) {
            state.stageCounters[3]++
            return mix
        }
        return 0
    }

    /**
     * OMEGA stage: finalize digest from crc and entropy hints.
     */
    fun omegaFinalize(): Int {
        val audit = NativeFastPath.coreAudit()
        if (audit != null && audit.size >= 2) {
            state.stageCounters[4]++
            state.crc32c = audit[0].toInt()
            state.entropyHint = audit[1].toInt()
            return state.crc32c xor state.entropyHint
        }
        return 0
    }

    /**
     * Get current triad state.
     */
    fun getTriad(): VectraTriad = triad

    /**
     * Get current state.
     */
    fun getState(): VectraState = state

    /**
     * Post an event to the event bus.
     */
    fun postEvent(event: VectraEvent) {
        eventBus?.post(event)
    }

    /**
     * Orchestrates deterministic CPU/Storage/Input/Output flow and returns routing/calc snapshot.
     */
    fun orchestrateFlow(
        cpuCycles: Long,
        storageReadBytes: Long,
        storageWriteBytes: Long,
        inputBytes: Long,
        outputBytes: Long,
        m00: Long,
        m01: Long,
        m10: Long,
        m11: Long
    ): VectraFlowSnapshot {
        if (!NativeFastPath.isNativeAvailable()) {
            return flowOrchestrator.orchestrate(
                cpuCycles,
                storageReadBytes,
                storageWriteBytes,
                inputBytes,
                outputBytes,
                m00,
                m01,
                m10,
                m11
            )
        }

        val native = NativeFastPath.processRoute(
            cpuCycles,
            storageReadBytes,
            storageWriteBytes,
            inputBytes,
            outputBytes,
            m00,
            m01,
            m10,
            m11
        )
        val route = when {
            native[0] >= native[1] && native[0] >= native[2] -> VectraFlowRoute.CPU_HEAVY
            native[1] >= native[2] -> VectraFlowRoute.STORAGE_HEAVY
            else -> VectraFlowRoute.IO_HEAVY
        }
        return VectraFlowSnapshot(
            orchestrationTag = native[4],
            route = route,
            cpuPressure = native[0].toInt(),
            storagePressure = native[1].toInt(),
            ioPressure = native[2].toInt(),
            matrixDeterminant = native[3]
        )
    }

    fun putDeterministicPath(path: String, payload: ByteArray, preferredLayer: Int = 0): VectraContainerEntry {
        return deterministicContainer.put(path, payload, preferredLayer)
    }

    fun readDeterministicPath(path: String): ByteArray? {
        return deterministicContainer.read(path)
    }

    fun buildDeterministicManifest(): VectraContainerManifest {
        return deterministicContainer.buildManifest()
    }
}
