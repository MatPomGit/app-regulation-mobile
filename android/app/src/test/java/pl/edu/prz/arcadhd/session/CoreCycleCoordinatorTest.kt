package pl.edu.prz.arcadhd.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityDecision
import pl.edu.prz.arcadhd.measurement.RecoveryFeatureModel
import pl.edu.prz.arcadhd.measurement.RecoveryMonitorConfig
import pl.edu.prz.arcadhd.measurement.RecoveryScore

class CoreCycleCoordinatorTest {
    private val context = RecoveryContext(
        taskRuleId = "rule-b",
        cognitiveDifficulty = 1,
        sensoryLoad = 0,
        responseWindowMs = 1500,
        stimulusDistributionId = "balanced-v1",
        cueCondition = "visual-only",
    )
    private val monitorConfig = RecoveryMonitorConfig(
        delta = 0.5,
        stabilityWindowUpdates = 3,
        censorLimitMs = 20_000,
    )
    private val recoveredScore = RecoveryScore(
        model = RecoveryFeatureModel.B,
        q = -0.1,
        behavioralDeficit = 0.1,
        slowingDeficit = null,
        variabilityDeficit = null,
    )

    @Test
    fun coreChallengeRegulationRecoveryCycleReturnsToBaseline() {
        var core = CoreCycleSnapshot(
            SessionSnapshot(
                state = SessionState.BASELINE_TASK,
                sequence = 7,
                lastTimestampMs = 1_000,
            ),
        )
        val eligible = ChallengeEligibilityDecision(
            eligible = true,
            reasons = emptySet(),
            validObservationCount = 12,
            goObservationCount = 6,
            noGoObservationCount = 6,
        )

        core = CoreCycleCoordinator.adjudicateChallenge(core, eligible, 2_000).snapshot
        assertEquals(SessionState.CHALLENGE_ACTIVE, core.session.state)

        core = CoreCycleCoordinator.challengeDelivered(core, 2_100).snapshot
        core = CoreCycleCoordinator.pauseComplete(core, 2_200).snapshot
        core = CoreCycleCoordinator.orientationComplete(core, 2_300).snapshot
        core = CoreCycleCoordinator.strategySelected(core, 2_400).snapshot
        core = CoreCycleCoordinator.strategyComplete(core, context, monitorConfig, 3_000).snapshot
        assertEquals(SessionState.RECOVERY_MONITORING, core.session.state)

        core = CoreCycleCoordinator.recoveryUpdate(core, recoveredScore, context, 4_000).snapshot
        core = CoreCycleCoordinator.recoveryUpdate(core, recoveredScore, context, 5_000).snapshot
        val observed = CoreCycleCoordinator.recoveryUpdate(core, recoveredScore, context, 6_000)
        assertNull(observed.rejection)
        core = observed.snapshot
        assertEquals(SessionState.RECOVERY_OBSERVED, core.session.state)
        assertEquals(1_000L, core.recoveryMonitor?.recoveryLatencyMs)

        core = CoreCycleCoordinator.persistRecovery(core, 6_100).snapshot
        assertEquals(SessionState.RESUME_GOAL, core.session.state)
        assertNull(core.recoveryMonitor)

        core = CoreCycleCoordinator.resumeGoal(core, 6_200).snapshot
        assertEquals(SessionState.BASELINE_TASK, core.session.state)
    }

    @Test
    fun suppressedChallengeReturnsToBaselineWithoutOpeningRecovery() {
        val core = CoreCycleSnapshot(
            SessionSnapshot(
                state = SessionState.BASELINE_TASK,
                sequence = 7,
                lastTimestampMs = 1_000,
            ),
        )
        val suppressed = ChallengeEligibilityDecision(
            eligible = false,
            reasons = setOf("safety_override"),
            validObservationCount = 12,
            goObservationCount = 6,
            noGoObservationCount = 6,
        )

        val result = CoreCycleCoordinator.adjudicateChallenge(core, suppressed, 2_000)
        assertNull(result.rejection)
        assertEquals(SessionState.BASELINE_TASK, result.snapshot.session.state)
        assertEquals(2, result.transitions.size)
        assertNull(result.snapshot.recoveryMonitor)
    }

    @Test
    fun explicitChildStopStillHasPriority() {
        val core = CoreCycleSnapshot(
            SessionSnapshot(
                state = SessionState.RECOVERY_MONITORING,
                sequence = 10,
                lastTimestampMs = 1_000,
                recoveryContext = context,
            ),
            recoveryMonitor = pl.edu.prz.arcadhd.measurement.RecoveryMonitorState(
                monitoringStartedAtMs = 1_000,
                config = monitorConfig,
            ),
        )
        val result = CoreCycleCoordinator.applyControls(
            core,
            SessionControls(childStop = true),
            1_100,
        )
        assertNull(result.rejection)
        assertEquals(SessionState.SAFE_STOP, result.snapshot.session.state)
        assertNull(result.snapshot.recoveryMonitor)
        assertTrue(result.transitions.single().reason == "explicit_child_stop")
    }
    @Test
    fun completedTaskMovesThroughReflectionToTerminalSession() {
        val baseline = CoreCycleSnapshot(
            SessionSnapshot(
                state = SessionState.BASELINE_TASK,
                sequence = 20,
                lastTimestampMs = 10_000,
            ),
        )
        val reflection = CoreCycleCoordinator.sessionGoalComplete(baseline, 11_000)
        assertNull(reflection.rejection)
        assertEquals(SessionState.SESSION_REFLECTION, reflection.snapshot.session.state)

        val completed = CoreCycleCoordinator.reflectionComplete(reflection.snapshot, 12_000)
        assertNull(completed.rejection)
        assertEquals(SessionState.SESSION_COMPLETE, completed.snapshot.session.state)
    }

}
