package com.vectras.vm.vectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectraDataOrchestratorTest {

    @Test
    fun orchestrate_cpuHeavyRoute_updatesCountersAndDeterminant() {
        val state = VectraState()
        val orchestrator = VectraDataOrchestrator(state)

        val snapshot = orchestrator.orchestrate(
            cpuCycles = 1_000_000,
            storageReadBytes = 64,
            storageWriteBytes = 64,
            inputBytes = 32,
            outputBytes = 16,
            m00 = 5,
            m01 = 2,
            m10 = 3,
            m11 = 7
        )

        assertEquals(VectraFlowRoute.CPU_HEAVY, snapshot.route)
        assertEquals(29, snapshot.matrixDeterminant)
        assertTrue(snapshot.orchestrationTag != 0L)
        assertEquals(32L, state.stageCounters[0])
        assertEquals(1_000_000L, state.stageCounters[1])
        assertEquals(128L, state.stageCounters[2])
        assertEquals(16L, state.stageCounters[3])
    }

    @Test
    fun orchestrate_sameInput_changesTagBySequence() {
        val state = VectraState()
        val orchestrator = VectraDataOrchestrator(state)

        val first = orchestrator.orchestrate(
            cpuCycles = 4096,
            storageReadBytes = 2048,
            storageWriteBytes = 2048,
            inputBytes = 512,
            outputBytes = 512,
            m00 = 8,
            m01 = 1,
            m10 = 2,
            m11 = 4
        )

        val second = orchestrator.orchestrate(
            cpuCycles = 4096,
            storageReadBytes = 2048,
            storageWriteBytes = 2048,
            inputBytes = 512,
            outputBytes = 512,
            m00 = 8,
            m01 = 1,
            m10 = 2,
            m11 = 4
        )

        assertEquals(first.route, second.route)
        assertNotEquals(first.orchestrationTag, second.orchestrationTag)
    }


    @Test
    fun orchestrate_monotonicCpuPressure_increasesWithCpu() {
        val state = VectraState()
        val orchestrator = VectraDataOrchestrator(state)

        val a = orchestrator.orchestrate(
            cpuCycles = 1024,
            storageReadBytes = 0,
            storageWriteBytes = 0,
            inputBytes = 0,
            outputBytes = 0,
            m00 = 1,
            m01 = 0,
            m10 = 0,
            m11 = 1
        )

        val b = orchestrator.orchestrate(
            cpuCycles = 2048,
            storageReadBytes = 0,
            storageWriteBytes = 0,
            inputBytes = 0,
            outputBytes = 0,
            m00 = 1,
            m01 = 0,
            m10 = 0,
            m11 = 1
        )

        assertTrue(b.cpuPressure >= a.cpuPressure)
    }

    @Test
    fun orchestrate_hysteresis_preventsFlipFlopNearTie() {
        val state = VectraState()
        val orchestrator = VectraDataOrchestrator(state)

        val first = orchestrator.orchestrate(
            cpuCycles = 4096,
            storageReadBytes = 4096,
            storageWriteBytes = 0,
            inputBytes = 0,
            outputBytes = 0,
            m00 = 2,
            m01 = 1,
            m10 = 1,
            m11 = 1
        )

        val second = orchestrator.orchestrate(
            cpuCycles = 4096,
            storageReadBytes = 4096,
            storageWriteBytes = 0,
            inputBytes = 0,
            outputBytes = 0,
            m00 = 2,
            m01 = 1,
            m10 = 1,
            m11 = 1
        )

        assertEquals(first.route, second.route)
    }

}