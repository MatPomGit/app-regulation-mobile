package pl.edu.prz.arcadhd.session

import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityDecision
import pl.edu.prz.arcadhd.measurement.RecoveryMonitorConfig
import pl.edu.prz.arcadhd.measurement.RecoveryMonitorState
import pl.edu.prz.arcadhd.measurement.RecoveryMonitorStatus
import pl.edu.prz.arcadhd.measurement.RecoveryScore

data class CoreCycleSnapshot(
    val session: SessionSnapshot,
    val recoveryMonitor: RecoveryMonitorState? = null,
)

data class CoreCycleDecision(
    val snapshot: CoreCycleSnapshot,
    val transitions: List<SessionTransition> = emptyList(),
    val rejection: String? = null,
)

/**
 * Connects already-adjudicated task/measurement services to the canonical session reducer.
 *
 * Safety, consent, device qualification and challenge eligibility remain external gates.
 * This coordinator does not infer them from performance and does not freeze Phase 0 values.
 */
object CoreCycleCoordinator {
    fun adjudicateChallenge(
        snapshot: CoreCycleSnapshot,
        eligibility: ChallengeEligibilityDecision,
        timestampMs: Long,
    ): CoreCycleDecision {
        if (snapshot.session.state != SessionState.BASELINE_TASK) {
            return reject(snapshot, "challenge_requires_baseline")
        }
        val pending = SessionController.reduce(
            snapshot = snapshot.session,
            event = "challenge_candidate",
            timestampMs = timestampMs,
        )
        if (pending.rejection != null) return reject(snapshot, pending.rejection)
        val pendingTransition = requireNotNull(pending.transition)

        val adjudicated = SessionController.reduce(
            snapshot = pending.snapshot,
            event = if (eligibility.eligible) "eligible" else "suppressed",
            timestampMs = timestampMs,
        )
        if (adjudicated.rejection != null) return reject(snapshot, adjudicated.rejection)
        return CoreCycleDecision(
            snapshot.copy(session = adjudicated.snapshot),
            listOf(pendingTransition, requireNotNull(adjudicated.transition)),
        )
    }

    fun challengeDelivered(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "challenge_delivered", timestampMs)

    fun pauseComplete(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "pause_complete", timestampMs)

    fun orientationComplete(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "orientation_complete", timestampMs)

    fun strategySelected(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "strategy_selected", timestampMs)

    fun strategyComplete(
        snapshot: CoreCycleSnapshot,
        context: RecoveryContext,
        monitorConfig: RecoveryMonitorConfig,
        timestampMs: Long,
    ): CoreCycleDecision {
        if (snapshot.recoveryMonitor != null) return reject(snapshot, "recovery_already_open")
        val decision = SessionController.reduce(
            snapshot = snapshot.session,
            event = "strategy_complete",
            timestampMs = timestampMs,
            context = context,
        )
        if (decision.rejection != null) return reject(snapshot, decision.rejection)
        return CoreCycleDecision(
            snapshot.copy(
                session = decision.snapshot,
                recoveryMonitor = RecoveryMonitorState(timestampMs, monitorConfig),
            ),
            listOf(requireNotNull(decision.transition)),
        )
    }

    fun recoveryUpdate(
        snapshot: CoreCycleSnapshot,
        score: RecoveryScore,
        context: RecoveryContext,
        timestampMs: Long,
    ): CoreCycleDecision {
        if (snapshot.session.state != SessionState.RECOVERY_MONITORING) {
            return reject(snapshot, "recovery_update_requires_monitoring")
        }
        val monitor = snapshot.recoveryMonitor ?: return reject(snapshot, "missing_recovery_monitor")
        val updated = monitor.update(timestampMs, score.q)
        val base = snapshot.copy(recoveryMonitor = updated)
        val event = when (updated.status) {
            RecoveryMonitorStatus.IN_PROGRESS -> return CoreCycleDecision(base)
            RecoveryMonitorStatus.OBSERVED -> "criterion_stable"
            RecoveryMonitorStatus.CENSORED -> "censor_limit_reached"
            RecoveryMonitorStatus.DATA_INVALID -> "required_data_invalid"
        }
        val decision = SessionController.reduce(
            snapshot = snapshot.session,
            event = event,
            timestampMs = timestampMs,
            context = if (updated.status == RecoveryMonitorStatus.DATA_INVALID) null else context,
        )
        if (decision.rejection != null) return reject(base, decision.rejection)
        return CoreCycleDecision(
            base.copy(session = decision.snapshot),
            listOf(requireNotNull(decision.transition)),
        )
    }

    fun invalidateRecovery(
        snapshot: CoreCycleSnapshot,
        reason: String,
        timestampMs: Long,
    ): CoreCycleDecision {
        if (snapshot.session.state != SessionState.RECOVERY_MONITORING) {
            return reject(snapshot, "recovery_invalidation_requires_monitoring")
        }
        val monitor = snapshot.recoveryMonitor ?: return reject(snapshot, "missing_recovery_monitor")
        val invalid = monitor.invalidate(timestampMs, reason)
        if (invalid.status != RecoveryMonitorStatus.DATA_INVALID) {
            return reject(snapshot, "recovery_invalidation_rejected")
        }
        val decision = SessionController.reduce(
            snapshot = snapshot.session,
            event = "required_data_invalid",
            timestampMs = timestampMs,
        )
        if (decision.rejection != null) return reject(snapshot, decision.rejection)
        return CoreCycleDecision(
            snapshot.copy(session = decision.snapshot, recoveryMonitor = invalid),
            listOf(requireNotNull(decision.transition)),
        )
    }

    fun persistRecovery(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision {
        val result = transition(snapshot, "event_persisted", timestampMs)
        return if (result.rejection == null) {
            result.copy(snapshot = result.snapshot.copy(recoveryMonitor = null))
        } else {
            result
        }
    }

    fun continuationAllowed(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision {
        val result = transition(snapshot, "continuation_allowed", timestampMs)
        return if (result.rejection == null) {
            result.copy(snapshot = result.snapshot.copy(recoveryMonitor = null))
        } else {
            result
        }
    }

    fun resumeGoal(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "continue_task", timestampMs)

    fun sessionGoalComplete(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "session_goal_complete", timestampMs)

    fun reflectionComplete(snapshot: CoreCycleSnapshot, timestampMs: Long): CoreCycleDecision =
        transition(snapshot, "reflection_complete", timestampMs)

    fun applyControls(
        snapshot: CoreCycleSnapshot,
        controls: SessionControls,
        timestampMs: Long,
    ): CoreCycleDecision {
        val decision = SessionController.reduce(
            snapshot = snapshot.session,
            timestampMs = timestampMs,
            controls = controls,
        )
        if (decision.rejection != null) return reject(snapshot, decision.rejection)
        val monitor = if (decision.snapshot.state in SessionGraph.terminal) null else snapshot.recoveryMonitor
        return CoreCycleDecision(
            snapshot.copy(session = decision.snapshot, recoveryMonitor = monitor),
            listOf(requireNotNull(decision.transition)),
        )
    }

    private fun transition(
        snapshot: CoreCycleSnapshot,
        event: String,
        timestampMs: Long,
    ): CoreCycleDecision {
        val decision = SessionController.reduce(
            snapshot = snapshot.session,
            event = event,
            timestampMs = timestampMs,
        )
        if (decision.rejection != null) return reject(snapshot, decision.rejection)
        return CoreCycleDecision(
            snapshot.copy(session = decision.snapshot),
            listOf(requireNotNull(decision.transition)),
        )
    }

    private fun reject(snapshot: CoreCycleSnapshot, reason: String?) =
        CoreCycleDecision(snapshot, rejection = reason ?: "unknown_rejection")
}
