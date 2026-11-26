package pl.edu.prz.arcadhd.measurement

data class EligibilityObservation(
    val isGo: Boolean,
    val valid: Boolean = true,
)

data class ChallengeEligibilityPolicy(
    val minimumReferenceObservations: Int,
    val minimumGoObservations: Int,
    val minimumNoGoObservations: Int,
    val minimumInterchallengeIntervalMs: Long,
    val minimumRemainingObservationMs: Long,
) {
    init {
        require(minimumReferenceObservations > 0)
        require(minimumGoObservations > 0)
        require(minimumNoGoObservations > 0)
        require(minimumGoObservations + minimumNoGoObservations <= minimumReferenceObservations)
        require(minimumInterchallengeIntervalMs >= 0)
        require(minimumRemainingObservationMs > 0)
    }

    companion object {
        /**
         * Mirrors the committed synthetic Phase 0 candidate. It is deliberately
         * labelled candidate and must not be treated as a frozen participant policy.
         */
        fun phase0Candidate() = ChallengeEligibilityPolicy(
            minimumReferenceObservations = 12,
            minimumGoObservations = 4,
            minimumNoGoObservations = 4,
            minimumInterchallengeIntervalMs = 30_000,
            minimumRemainingObservationMs = 20_000,
        )
    }
}

data class ChallengeEligibilityInput(
    val recentObservations: List<EligibilityObservation>,
    val nowMs: Long,
    val sessionEndMs: Long,
    val lastChallengeMs: Long? = null,
    val validTaskState: Boolean = true,
    val telemetryComplete: Boolean = true,
    val safetyOverride: Boolean = false,
    val sensoryOverload: Boolean = false,
    val pausedOrStopped: Boolean = false,
) {
    init {
        require(nowMs >= 0)
        require(sessionEndMs >= nowMs)
        require(lastChallengeMs == null || lastChallengeMs in 0L..nowMs)
    }
}

data class ChallengeEligibilityDecision(
    val eligible: Boolean,
    val reasons: Set<String>,
    val validObservationCount: Int,
    val goObservationCount: Int,
    val noGoObservationCount: Int,
)

object ChallengeEligibilityService {
    fun evaluate(
        policy: ChallengeEligibilityPolicy,
        input: ChallengeEligibilityInput,
    ): ChallengeEligibilityDecision {
        val valid = input.recentObservations.filter { it.valid }
            .takeLast(policy.minimumReferenceObservations)
        val goCount = valid.count { it.isGo }
        val noGoCount = valid.count { !it.isGo }
        val reasons = linkedSetOf<String>()

        if (!input.validTaskState) reasons += "invalid_task_state"
        if (!input.telemetryComplete) reasons += "incomplete_reference_telemetry"
        if (input.safetyOverride) reasons += "safety_override"
        if (input.sensoryOverload) reasons += "sensory_overload"
        if (input.pausedOrStopped) reasons += "recent_pause_or_stop"
        if (valid.size < policy.minimumReferenceObservations) reasons += "insufficient_reference_observations"
        if (goCount < policy.minimumGoObservations) reasons += "insufficient_go_observations"
        if (noGoCount < policy.minimumNoGoObservations) reasons += "insufficient_nogo_observations"

        val sinceLast = input.lastChallengeMs?.let { input.nowMs - it }
        if (sinceLast != null && sinceLast < policy.minimumInterchallengeIntervalMs) {
            reasons += "interchallenge_interval"
        }
        if (input.sessionEndMs - input.nowMs < policy.minimumRemainingObservationMs) {
            reasons += "insufficient_remaining_observation"
        }

        return ChallengeEligibilityDecision(
            eligible = reasons.isEmpty(),
            reasons = reasons,
            validObservationCount = valid.size,
            goObservationCount = goCount,
            noGoObservationCount = noGoCount,
        )
    }
}
