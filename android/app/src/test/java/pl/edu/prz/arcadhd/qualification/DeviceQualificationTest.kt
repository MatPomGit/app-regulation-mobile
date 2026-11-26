package pl.edu.prz.arcadhd.qualification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceQualificationTest {
    private val policy = DeviceQualificationPolicy.engineeringCandidate()

    @Test
    fun completeEngineeringEvidenceCanQualifyCore() {
        val decision = DeviceQualificationService.evaluate(
            policy,
            DeviceQualificationEvidence(
                sdkInt = 35,
                widthDp = 360,
                heightDp = 780,
                touchInputVerified = true,
                monotonicClockVerified = true,
                durableStorageVerified = true,
                offlineCoreVerified = true,
                backgroundPauseVerified = true,
                medianFrameMs = 16.7,
                timerJitterP95Ms = 5.0,
            ),
        )
        assertTrue(decision.qualifiedCore)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test
    fun missingTimingEvidenceCannotBeSilentlyQualified() {
        val decision = DeviceQualificationService.evaluate(
            policy,
            DeviceQualificationEvidence(
                sdkInt = 35,
                widthDp = 360,
                heightDp = 780,
                touchInputVerified = true,
                monotonicClockVerified = true,
                durableStorageVerified = true,
                offlineCoreVerified = true,
                backgroundPauseVerified = true,
                medianFrameMs = null,
                timerJitterP95Ms = null,
            ),
        )
        assertFalse(decision.qualifiedCore)
        assertTrue("frame_benchmark_missing" in decision.reasons)
        assertTrue("timer_jitter_missing" in decision.reasons)
    }
}
