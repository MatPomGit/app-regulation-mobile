package pl.edu.prz.arcadhd.storage

import org.junit.Assert.*
import org.junit.Test
import pl.edu.prz.arcadhd.session.*

class RestartRecoveryTest {
    private fun stored(snapshot: SessionSnapshot) = StoredSession("synthetic-session", snapshot, "boot-A", "process-A")

    @Test fun baselineRestartsIntoExplicitTechnicalInterruption() {
        val snapshot = SessionSnapshot(state = SessionState.BASELINE_TASK, lastTimestampMs = 50)
        val plan = RestartRecovery.plan(stored(snapshot), "boot-A", "process-B", 100)
        assertFalse(plan.readOnly)
        assertEquals(SessionState.TECHNICAL_INTERRUPT, plan.command!!.apply(snapshot).snapshot.state)
        assertEquals(SessionState.BASELINE_TASK, plan.command.apply(snapshot).snapshot.resumeTarget)
    }

    @Test fun interruptedRecoveryAbortsWithoutObservedOrCensoredOutcome() {
        for (state in listOf(SessionState.RECOVERY_MONITORING, SessionState.PAUSED_BY_CHILD)) {
            val snapshot = SessionSnapshot(
                state = state, recoveryContext = RecoveryContext("A", 1, 0, 1800, "synthetic", "none"),
            )
            val plan = RestartRecovery.plan(stored(snapshot), "boot-A", "process-B", 100)
            assertEquals("recovery_interrupted_protocol_abort", plan.reason)
            assertEquals(SessionState.PROTOCOL_ABORT, plan.command!!.apply(snapshot).snapshot.state)
        }
    }

    @Test fun rebootOrBackwardClockRestoresOnlyForInspection() {
        val value = stored(SessionSnapshot(state = SessionState.BASELINE_TASK, lastTimestampMs = 50))
        assertTrue(RestartRecovery.plan(value, "boot-B", "process-B", 100).readOnly)
        assertTrue(RestartRecovery.plan(value, "boot-A", "process-B", 10).readOnly)
        assertNull(RestartRecovery.plan(value, "boot-B", "process-B", 100).command)
    }

    @Test fun sameProcessAndCompletedSessionsNeverGenerateDuplicateInterruption() {
        val value = stored(SessionSnapshot(state = SessionState.BASELINE_TASK))
        assertNull(RestartRecovery.plan(value, "boot-A", "process-A", 100).command)
        val terminal = stored(SessionSnapshot(state = SessionState.SESSION_COMPLETE))
        assertNull(RestartRecovery.plan(terminal, "boot-A", "process-B", 100).command)
    }

    @Test fun codecRoundTripsAndRejectsUnknownVersionsAndTrailingBytes() {
        val snapshot = SessionSnapshot(
            state = SessionState.PAUSED_BY_CHILD, mode = SessionMode.ACTIVE_CONTROL,
            sequence = 3, lastTimestampMs = 123, resumeTarget = SessionState.BASELINE_TASK,
        )
        assertEquals(snapshot, SessionJournalCodec.snapshot(SessionJournalCodec.snapshot(snapshot)))
        val command = SessionCommand(
            null, 123, SessionControls(childStop = true), expectedSequence = 3,
        )
        assertEquals(command, SessionJournalCodec.command(SessionJournalCodec.command(command)))
        val bytes = java.util.Base64.getDecoder().decode(SessionJournalCodec.snapshot(snapshot))
        bytes[3] = 99
        try {
            SessionJournalCodec.snapshot(java.util.Base64.getEncoder().encodeToString(bytes))
            throw AssertionError("Unknown version accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            SessionJournalCodec.snapshot(java.util.Base64.getEncoder().encodeToString(
                java.util.Base64.getDecoder().decode(SessionJournalCodec.snapshot(snapshot)) + byteArrayOf(1),
            ))
            throw AssertionError("Trailing data accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
