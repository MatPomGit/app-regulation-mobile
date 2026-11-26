package pl.edu.prz.arcadhd.measurement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeEligibilityTest {
    private val policy = ChallengeEligibilityPolicy.phase0Candidate()
    private val balanced = List(6) { EligibilityObservation(isGo = true) } +
        List(6) { EligibilityObservation(isGo = false) }

    @Test
    fun eligibleCandidatePassesAllEngineeringGates() {
        val decision = ChallengeEligibilityService.evaluate(
            policy,
            ChallengeEligibilityInput(
                recentObservations = balanced,
                nowMs = 40_000,
                sessionEndMs = 70_000,
            ),
        )
        assertTrue(decision.eligible)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test
    fun safetyAndInsufficientWindowBlockChallenge() {
        val decision = ChallengeEligibilityService.evaluate(
            policy,
            ChallengeEligibilityInput(
                recentObservations = balanced.take(8),
                nowMs = 40_000,
                sessionEndMs = 50_000,
                safetyOverride = true,
            ),
        )
        assertFalse(decision.eligible)
        assertTrue("safety_override" in decision.reasons)
        assertTrue("insufficient_reference_observations" in decision.reasons)
        assertTrue("insufficient_remaining_observation" in decision.reasons)
    }

    @Test
    fun interchallengeIntervalIsEnforced() {
        val decision = ChallengeEligibilityService.evaluate(
            policy,
            ChallengeEligibilityInput(
                recentObservations = balanced,
                nowMs = 40_000,
                sessionEndMs = 80_000,
                lastChallengeMs = 20_000,
            ),
        )
        assertFalse(decision.eligible)
        assertTrue("interchallenge_interval" in decision.reasons)
    }
}
