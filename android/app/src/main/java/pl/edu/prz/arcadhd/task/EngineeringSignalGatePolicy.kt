package pl.edu.prz.arcadhd.task

import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityDecision
import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityInput
import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityPolicy
import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityService
import pl.edu.prz.arcadhd.measurement.EligibilityObservation

/**
 * Engineering-only rule for demonstrating the Core challenge transition after four trials.
 *
 * It is intentionally separate from the Phase 0 candidate policy, which requires a larger
 * reference window and remains the scientific source of truth.
 */
object EngineeringSignalGatePolicy {
    private val policy = ChallengeEligibilityPolicy(
        minimumReferenceObservations = 4,
        minimumGoObservations = 2,
        minimumNoGoObservations = 2,
        minimumInterchallengeIntervalMs = 0,
        minimumRemainingObservationMs = 1,
    )

    fun challengeDecision(
        responses: List<TimedGateResponse>,
        nowMs: Long,
    ): ChallengeEligibilityDecision {
        val observations = responses.takeLast(4).map {
            EligibilityObservation(isGo = it.expectedGo, valid = true)
        }
        return ChallengeEligibilityService.evaluate(
            policy,
            ChallengeEligibilityInput(
                recentObservations = observations,
                nowMs = nowMs,
                sessionEndMs = nowMs + 1,
            ),
        )
    }
}
