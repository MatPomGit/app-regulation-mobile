package pl.edu.prz.arcadhd.task

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringSignalGatePolicyTest {
    @Test
    fun fourBalancedEngineeringTrialsOpenTheDemoChallenge() {
        val responses = listOf(
            response(0, expectedGo = true),
            response(1, expectedGo = false),
            response(2, expectedGo = false),
            response(3, expectedGo = true),
        )
        val decision = EngineeringSignalGatePolicy.challengeDecision(responses, 10_000)
        assertTrue(decision.eligible)
    }

    @Test
    fun incompleteEngineeringReferenceRemainsSuppressed() {
        val decision = EngineeringSignalGatePolicy.challengeDecision(
            listOf(
                response(0, expectedGo = true),
                response(1, expectedGo = false),
                response(2, expectedGo = true),
            ),
            10_000,
        )
        assertFalse(decision.eligible)
    }

    private fun response(index: Int, expectedGo: Boolean) = TimedGateResponse(
        trialId = "run-t${index.toString().padStart(3, '0')}",
        trialIndex = index,
        rule = GateRule.A,
        stimulus = if (expectedGo) GateColor.BLUE else GateColor.ORANGE,
        kind = if (expectedGo) TimedResponseKind.GO_TAP else TimedResponseKind.WITHHOLD_TIMEOUT,
        expectedGo = expectedGo,
        correct = true,
        shownAtMs = 1_000L + index * 2_000L,
        respondedAtMs = 1_500L + index * 2_000L,
    )
}
