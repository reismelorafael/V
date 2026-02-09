package com.vectras.vm.vectra

import android.content.Context
import android.util.Log
import com.vectras.vm.BuildConfig
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "VectraCore"

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
data class VectraEvent(
    val type: EventType,
    val priority: Int, // Higher = more urgent
    val timestamp: Long = System.nanoTime(),
    val payload: ByteArray? = null
) : Comparable<VectraEvent> {
    enum class EventType {
        TIMER_TICK,
        NETWORK_CHANGE,
        RADIO_EVENT,
        USER_INPUT,
        SYSTEM_EVENT
    }

    override fun compareTo(other: VectraEvent): Int {
        // Higher priority first, then older timestamp first
        val priorityDiff = other.priority - this.priority
        return if (priorityDiff != 0) priorityDiff else (timestamp - other.timestamp).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectraEvent
        if (type != other.type) return false
        if (priority != other.priority) return false
        if (timestamp != other.timestamp) return false
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
        result = 31 * result + (payload?.contentHashCode() ?: 0)
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
        private const val FLOW_MIX_C = 0x9E3779B97F4A7C15uL.toLong()
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
    private val queue = PriorityQueue<VectraEvent>()
    private val lock = ReentrantLock()

    fun post(event: VectraEvent) {
        lock.withLock {
            queue.add(event)
        }
    }

    fun poll(): VectraEvent? {
        lock.withLock {
            return queue.poll()
        }
    }

    fun size(): Int {
        lock.withLock {
            return queue.size
        }
    }

    fun clear() {
        lock.withLock {
            queue.clear()
        }
    }
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
            Log.d(TAG, "VectraCycle started")
            while (running.get()) {
                try {
                    executeCycle()
                    Thread.sleep(100) // 10 Hz cycle rate
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Cycle error", e)
                }
            }
            Log.d(TAG, "VectraCycle stopped")
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
        val inputBytes = event?.payload?.size?.toLong() ?: 0L
        state.stageCounters[0] += inputBytes
        updatePolicy(event)

        // Phase 2: Process
        if (event != null) {
            processEvent(event)
        }

        // Phase 3: Output
        logger?.let {
            val payload = event?.payload ?: ByteArray(0)
            val meta = (event?.type?.ordinal ?: 0) or ((event?.priority ?: 0) shl 8)
            it.append(payload, meta)
            state.stageCounters[3] += payload.size.toLong()
        }

        // Phase 4: Next
        cycleCount++
        state.stageCounters[5] = cycleCount // Track total cycles
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
        
        if (event.payload != null) {
            val entropy = CRC32C.update(state.entropyHint, event.payload)
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
        private const val RECORD_HEADER_SIZE = 16 // magic(4) + len(4) + meta(4) + crc(4)
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10 MB
        private const val FLUSH_INTERVAL_MS = 1000L
        private const val FLUSH_RECORDS = 32
    }

    private val file: RandomAccessFile = RandomAccessFile(logFile, "rw")
    private val lock = ReentrantLock()
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
            val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(MAGIC.toInt())
            header.putInt(VERSION)
            header.putLong(System.currentTimeMillis())
            file.write(header.array())
            file.channel.force(true)
        }
    }

    /**
     * Appends a record: [u32 magic, u32 len, u32 meta, u32 crc, payload]
     */
    fun append(payload: ByteArray, meta: Int = 0) {
        lock.withLock {
            if (file.length() >= MAX_LOG_SIZE) {
                Log.w(TAG, "Log size exceeded, skipping append")
                return
            }

            val recordHeader = ByteBuffer.allocate(RECORD_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            recordHeader.putInt(MAGIC.toInt())
            recordHeader.putInt(payload.size)
            recordHeader.putInt(meta)
            
            // Compute CRC incrementally without concatenation
            var crc = CRC32C.update(0, recordHeader.array(), 0, 12)
            crc = CRC32C.update(crc, payload)
            recordHeader.putInt(crc)

            file.write(recordHeader.array())
            file.write(payload)
            recordsSinceFlush++
            maybeFlush()
            recordCount++
        }
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
    private val state = VectraState()
    private val triad = VectraTriad()
    private val mempool = VectraMemPool(DEFAULT_CHUNK_BYTES, DEFAULT_POOL_SIZE)
    private val flowOrchestrator = VectraDataOrchestrator(state)
    private val deterministicContainer = VectraDeterministicContainer(state = state)
    private var eventBus: VectraEventBus? = null
    private var cycle: VectraCycle? = null
    private var logger: VectraBitStackLog? = null
    private val initialized = AtomicBoolean(false)

    @JvmStatic
    fun init(context: Context) {
        if (!BuildConfig.VECTRA_CORE_ENABLED) {
            Log.d(TAG, "VectraCore disabled by BuildConfig")
            return
        }

        if (initialized.getAndSet(true)) {
            Log.d(TAG, "VectraCore already initialized")
            return
        }

        state.seed = (System.nanoTime() and 0x7FFFFFFF).toInt()
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

        Log.d(TAG, "init seed=${state.seed} crc=${state.crc32c} entropy=${state.entropyHint} logPath=${logFile.absolutePath}")
    }

    /**
     * Self-test: Creates a block, mutates a copy, detects changes.
     * Validates CRC and parity detection (1-bit-safe ethos).
     */
    private fun selfTest(header: ByteArray) {
        Log.d(TAG, "Running self-test...")
        
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
        logger?.append(result.array(), 0xFFFF) // meta=0xFFFF for self-test
        
        Log.d(TAG, "selftest_ok=$allOk headerOk=$headerOk detectsChange=$detectsChange parityOk=$parityOk detectsBitFlip=$detectsBitFlip syndromeOk=$syndromeOk syndrome=$syndrome")
    }

    /**
     * Starts a background thread that posts timer tick events.
     */
    private fun startTimerTicks() {
        // Pre-allocate buffer to avoid GC pressure (runs every second)
        val timestampBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        Thread {
            while (initialized.get()) {
                try {
                    timestampBuffer.clear()
                    timestampBuffer.putLong(System.currentTimeMillis())
                    val payload = timestampBuffer.array().copyOf() // Copy to avoid sharing mutable buffer
                    eventBus?.post(
                        VectraEvent(
                            type = VectraEvent.EventType.TIMER_TICK,
                            priority = 1,
                            payload = payload
                        )
                    )
                    Thread.sleep(1000) // 1 Hz tick
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Timer tick error", e)
                }
            }
        }.apply {
            name = "VectraTimerTick"
            isDaemon = true
            start()
        }
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
        Log.d(TAG, "VectraCore shutdown complete")
    }

    /**
     * PSI stage: fold payload into CRC and advance stage counter (deterministic ingest).
     * Noise is data (ρ = information not decoded yet).
     */
    fun psi(payload: ByteArray): Int {
        val crc = CRC32C.update(state.crc32c, payload)
        state.crc32c = crc
        state.stageCounters[0]++
        return crc
    }

    /**
     * RHO stage: treat noise as data to update entropy hint.
     * Do not treat noise as simple bug - it's information not decoded yet.
     */
    fun rho(noise: ByteArray, eventWeight: Int = 1): Int {
        val entropy = CRC32C.update(state.entropyHint, noise)
        state.entropyHint = entropy + eventWeight
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
