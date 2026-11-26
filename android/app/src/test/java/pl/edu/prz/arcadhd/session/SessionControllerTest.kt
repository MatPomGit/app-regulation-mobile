package pl.edu.prz.arcadhd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerTest {
    private val context = RecoveryContext("A", 1, 0, 1800, "synthetic-test", "none")

    private fun apply(
        snapshot: SessionSnapshot,
        event: String,
        currentContext: RecoveryContext? = context,
    ): SessionSnapshot {
        val decision = SessionController.reduce(
            snapshot, event, snapshot.lastTimestampMs + 1, context = currentContext,
        )
        assertNull("Rejected $event from ${snapshot.state}", decision.rejection)
        assertNotNull(decision.transition)
        return decision.snapshot
    }

    private fun baseline(mode: SessionMode = SessionMode.CORE): SessionSnapshot {
        var snapshot = SessionSnapshot(mode = mode)
        listOf(
            "initialized", "configuration_loaded", "qualified_core",
            "onboarding_complete", "tutorial_required", "comprehension_met",
            "calibration_complete",
        ).forEach { snapshot = apply(snapshot, it) }
        assertEquals(SessionState.BASELINE_TASK, snapshot.state)
        return snapshot
    }

    private fun recovery(): SessionSnapshot {
        var snapshot = baseline()
        listOf(
            "challenge_candidate", "eligible", "challenge_delivered", "pause_complete",
            "orientation_complete", "strategy_selected", "strategy_complete",
        ).forEach { snapshot = apply(snapshot, it) }
        assertEquals(SessionState.RECOVERY_MONITORING, snapshot.state)
        return snapshot
    }

    @Test fun observedAndCensoredRecoveryReturnToTheGoalOnlyAfterPersistenceAck() {
        listOf(
            "criterion_stable" to SessionState.RECOVERY_OBSERVED,
            "censor_limit_reached" to SessionState.RECOVERY_CENSORED,
        ).forEach { (event, target) ->
            val closed = apply(recovery(), event)
            assertEquals(target, closed.state)
            assertNotNull(closed.recoveryContext)
            assertEquals("event_not_allowed", SessionController.reduce(
                closed, "continue_task", closed.lastTimestampMs + 1,
            ).rejection)
            val resumed = apply(closed, "event_persisted")
            assertEquals(SessionState.RESUME_GOAL, resumed.state)
            assertNull(resumed.recoveryContext)
            assertEquals(SessionState.BASELINE_TASK, apply(resumed, "continue_task").state)
        }
    }

    @Test fun contextIsRequiredAndCannotBeChangedToClaimRecovery() {
        val before = apply(baseline(), "challenge_candidate")
        var snapshot = apply(before, "eligible")
        listOf("challenge_delivered", "pause_complete", "orientation_complete", "strategy_selected")
            .forEach { snapshot = apply(snapshot, it) }
        assertEquals("recovery_context_required", SessionController.reduce(
            snapshot, "strategy_complete", snapshot.lastTimestampMs + 1,
        ).rejection)
        val monitoring = recovery()
        val changed = context.copy(responseWindowMs = 1500)
        assertEquals("recovery_outcome_context_unverified", SessionController.reduce(
            monitoring, "criterion_stable", monitoring.lastTimestampMs + 1, context = changed,
        ).rejection)
        val invalid = apply(monitoring, "measurement_context_changed", changed)
        assertEquals(SessionState.RECOVERY_DATA_INVALID, invalid.state)
        assertEquals(context, invalid.recoveryContext)
    }

    @Test fun technicalFailureIsInvalidNotCensored() {
        assertEquals(SessionState.RECOVERY_DATA_INVALID,
            apply(recovery(), "required_data_invalid").state)
        val interrupted = apply(recovery(), "technical_interrupt")
        assertEquals(SessionState.RECOVERY_MONITORING, interrupted.resumeTarget)
        assertEquals(SessionState.RECOVERY_DATA_INVALID,
            apply(interrupted, "recovery_invalidated").state)
        val ordinary = apply(baseline(), "technical_interrupt")
        assertEquals("no_recovery_to_invalidate", SessionController.reduce(
            ordinary, "recovery_invalidated", ordinary.lastTimestampMs + 1,
        ).rejection)
    }

    @Test fun pauseAndTechnicalInterruptionStoreAnExplicitResumeTarget() {
        val baseline = baseline()
        val paused = apply(baseline, "pause")
        assertEquals(SessionState.BASELINE_TASK, paused.resumeTarget)
        assertEquals(SessionState.BASELINE_TASK, apply(paused, "resume").state)
        val strategy = SessionSnapshot(state = SessionState.STRATEGY_EXECUTION)
        assertEquals(SessionState.STRATEGY_EXECUTION,
            apply(apply(strategy, "pause"), "resume").state)
        val interrupted = apply(baseline, "technical_interrupt")
        assertEquals(SessionState.BASELINE_TASK, apply(interrupted, "resume_valid").state)
        val empty = SessionSnapshot(state = SessionState.PAUSED_BY_CHILD)
        assertEquals("missing_resume_target", SessionController.reduce(
            empty, "resume", 1,
        ).rejection)
        assertEquals("invalid_resume_target", SessionController.reduce(
            empty.copy(resumeTarget = SessionState.SESSION_COMPLETE), "resume", 1,
        ).rejection)
    }

    @Test fun recoveryResumeNeedsTheOriginalVerifiedContext() {
        for ((pauseEvent, resumeEvent) in listOf("pause" to "resume", "technical_interrupt" to "resume_valid")) {
            val paused = apply(recovery(), pauseEvent)
            assertEquals("recovery_resume_context_unverified", SessionController.reduce(
                paused, resumeEvent, paused.lastTimestampMs + 1,
            ).rejection)
            assertEquals(SessionState.RECOVERY_MONITORING, apply(paused, resumeEvent).state)
        }
    }

    @Test fun globalPriorityWinsOverOrdinaryProgressionAndStaleCallbacks() {
        val monitoring = recovery()
        val fatal = SessionController.reduce(
            monitoring, "criterion_stable", monitoring.lastTimestampMs + 1,
            SessionControls(fatalIntegrity = true, safetyStop = true, childStop = true,
                childPause = true, protocolAbort = true, technicalInterrupt = true),
            expectedSequence = 0,
        )
        assertEquals(SessionState.FATAL_ERROR, fatal.snapshot.state)
        assertEquals("fatal_integrity_condition", fatal.transition?.reason)
        val safety = SessionController.reduce(
            monitoring, "criterion_stable", monitoring.lastTimestampMs + 1,
            SessionControls(safetyStop = true, childPause = true),
        )
        assertEquals(SessionState.SAFE_STOP, safety.snapshot.state)
        assertEquals("safety_stop", safety.transition?.reason)
        assertEquals(SessionState.SAFE_STOP, SessionController.reduce(
            SessionSnapshot(), timestampMs = 1, controls = SessionControls(childStop = true),
        ).snapshot.state)
        val paused = SessionController.reduce(
            baseline(), "challenge_candidate", 100,
            SessionControls(childPause = true, protocolAbort = true, technicalInterrupt = true),
        )
        assertEquals(SessionState.PAUSED_BY_CHILD, paused.snapshot.state)
        assertEquals(SessionState.PROTOCOL_ABORT, SessionController.reduce(
            baseline(), timestampMs = 100,
            controls = SessionControls(protocolAbort = true, technicalInterrupt = true),
        ).snapshot.state)
    }

    @Test fun terminalStatesRejectAllEventsAndSafetyDoesNotCreateAnotherOutcome() {
        SessionGraph.terminal.forEach { state ->
            val snapshot = SessionSnapshot(state = state)
            val decision = SessionController.reduce(
                snapshot, "initialized", 1, SessionControls(fatalIntegrity = true),
            )
            assertEquals(snapshot, decision.snapshot)
            assertEquals("terminal_state", decision.rejection)
            assertNull(decision.transition)
        }
        val observed = apply(recovery(), "criterion_stable")
        val failed = apply(observed, "persistence_failure")
        assertEquals(SessionState.FATAL_ERROR, failed.state)
    }

    @Test fun staleEventsAndBackwardClockDoNotAdvanceSequence() {
        val snapshot = baseline()
        assertEquals("stale_sequence", SessionController.reduce(
            snapshot, "challenge_candidate", 100, expectedSequence = 0,
        ).rejection)
        assertEquals("non_monotonic_timestamp", SessionController.reduce(
            snapshot, "challenge_candidate", snapshot.lastTimestampMs - 1,
        ).rejection)
        assertEquals("event_not_allowed", SessionController.reduce(
            snapshot, "criterion_stable", 100,
        ).rejection)
        val decision = SessionController.reduce(snapshot, "challenge_candidate", 100)
        assertEquals(snapshot.sequence + 1, decision.transition?.sequence)
        assertEquals(SessionGraph.SOURCE_SHA256, decision.transition?.configurationHash)
    }

    @Test fun activeControlNeverEntersChallengeRegulationOrRecovery() {
        val snapshot = baseline(SessionMode.ACTIVE_CONTROL)
        assertEquals("active_control_boundary", SessionController.reduce(
            snapshot, "challenge_candidate", 100,
        ).rejection)
        val reflection = apply(snapshot, "session_goal_complete")
        assertEquals(SessionState.SESSION_COMPLETE, apply(reflection, "reflection_complete").state)
    }

    @Test fun suppressedChallengeReturnsToBaselineWithoutRecoveryContext() {
        val pending = apply(baseline(), "challenge_candidate")
        val suppressed = apply(pending, "suppressed")
        assertEquals(SessionState.BASELINE_TASK, suppressed.state)
        assertNull(suppressed.recoveryContext)
    }

    @Test fun everyCommittedGraphEdgeCanBeExecutedWithExplicitRequiredInputs() {
        SessionGraph.transitions.forEach { (source, edges) ->
            edges.forEach { (event, target) ->
                val returnTarget = if (source in setOf(
                    SessionState.PAUSED_BY_CHILD, SessionState.TECHNICAL_INTERRUPT,
                )) SessionState.BASELINE_TASK else null
                val snapshot = SessionSnapshot(
                    state = source, resumeTarget = returnTarget,
                    recoveryContext = context,
                )
                val decision = SessionController.reduce(
                    snapshot, event, 1, context = context,
                )
                assertNull("$source / $event: ${decision.rejection}", decision.rejection)
                assertEquals(target ?: returnTarget, decision.snapshot.state)
            }
        }
    }

    @Test fun replayAndContextHashesAreDeterministic() {
        val snapshot = recovery()
        assertEquals(
            SessionController.reduce(snapshot, "criterion_stable", 100, context = context),
            SessionController.reduce(snapshot, "criterion_stable", 100, context = context),
        )
        assertEquals(
            "3d156b19b80e99673e3f4ea923f0150c90fff89f7df5a0a8215a36bd8e49a3e6",
            context.hash(),
        )
        assertEquals(context.hash(), context.copy().hash())
        assertEquals(64, context.hash().length)
        assertTrue(context.hash().all { it in "0123456789abcdef" })
        assertNotEquals(context.hash(), context.copy(taskRuleId = "B").hash())
        assertNotEquals(context.hash(), context.copy(sensoryLoad = 1).hash())
    }
}
