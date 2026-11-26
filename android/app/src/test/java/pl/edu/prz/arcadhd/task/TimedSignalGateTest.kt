package pl.edu.prz.arcadhd.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedSignalGateTest {
    @Test
    fun recordsMonotonicResponsesAndTimeouts() {
        var gate = TimedSignalGate(responseWindowMs = 1000, runId = "run").start(100)
        val first = requireNotNull(gate.currentTrial)
        assertEquals("run-t000", first.id)

        gate = gate.submitGo(first.id, 400)
        assertEquals(1, gate.responses.size)
        assertTrue(gate.responses.single().correct)
        assertEquals(300, gate.responses.single().latencyMs)

        val second = requireNotNull(gate.currentTrial)
        gate = gate.tick(second.deadlineAtMs)
        assertEquals(2, gate.responses.size)
        assertEquals(TimedResponseKind.WITHHOLD_TIMEOUT, gate.responses.last().kind)
        assertTrue(gate.responses.last().correct)
    }

    @Test
    fun staleCallbackAndBackwardClockAreRejected() {
        val started = TimedSignalGate(responseWindowMs = 1000, runId = "run").start(100)
        assertEquals(started, started.submitGo("wrong-id", 200))
        assertEquals(started, started.tick(99))
    }

    @Test
    fun pauseShiftsDeadlineAndRequiresExplicitResume() {
        val started = TimedSignalGate(responseWindowMs = 1000, runId = "run").start(100)
        val original = requireNotNull(started.currentTrial)
        val paused = started.pause(200)
        assertEquals(TimedGateStage.PAUSED, paused.stage)

        val resumed = paused.resume(1200)
        val shifted = requireNotNull(resumed.currentTrial)
        assertEquals(original.shownAtMs + 1000, shifted.shownAtMs)
        assertEquals(original.deadlineAtMs + 1000, shifted.deadlineAtMs)
        assertEquals(TimedGateStage.TASK, resumed.stage)
    }

    @Test
    fun ruleSwitchRequiresAcknowledgement() {
        var gate = TimedSignalGate(responseWindowMs = 1000, runId = "run").start(100)
        repeat(4) {
            val trial = requireNotNull(gate.currentTrial)
            gate = if (trial.stimulus == GateColor.BLUE) {
                gate.submitGo(trial.id, trial.shownAtMs + 100)
            } else {
                gate.tick(trial.deadlineAtMs)
            }
        }

        assertEquals(TimedGateStage.RULE_SWITCH, gate.stage)
        assertEquals(4, gate.trialIndex)
        assertEquals(null, gate.currentTrial)

        val acknowledged = gate.acknowledgeRuleSwitch(gate.lastTimestampMs + 1)
        assertEquals(TimedGateStage.TASK, acknowledged.stage)
        assertEquals(GateRule.B, requireNotNull(acknowledged.currentTrial).rule)
    }

    @Test
    fun stopIsTerminalForEngineeringRun() {
        val started = TimedSignalGate(runId = "run").start(100)
        val stopped = started.stop(200)
        assertEquals(TimedGateStage.STOPPED, stopped.stage)
        assertFalse(stopped.isActive())
        assertEquals(stopped, stopped.stop(300))
    }

    private fun TimedSignalGate.isActive(): Boolean =
        stage == TimedGateStage.TASK || stage == TimedGateStage.RULE_SWITCH
    @Test
    fun durableCommitDelayCanBeExcludedFromNextTrialTiming() {
        var gate = TimedSignalGate(responseWindowMs = 1000, runId = "run").start(100)
        val first = requireNotNull(gate.currentTrial)
        gate = gate.submitGo(first.id, 300)

        val preparedSecond = requireNotNull(gate.currentTrial)
        assertEquals(300L, preparedSecond.shownAtMs)

        gate = gate.reanchorCurrentTrial(800)
        val presentedSecond = requireNotNull(gate.currentTrial)
        assertEquals(800L, presentedSecond.shownAtMs)
        assertEquals(1800L, presentedSecond.deadlineAtMs)
        assertEquals(1, gate.responses.size)
        assertEquals(200L, gate.responses.single().latencyMs)
    }

}
