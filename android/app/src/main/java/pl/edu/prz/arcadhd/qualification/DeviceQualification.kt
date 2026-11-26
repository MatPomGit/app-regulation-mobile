package pl.edu.prz.arcadhd.qualification

data class DeviceQualificationPolicy(
    val minimumSdk: Int,
    val minimumWidthDp: Int,
    val minimumHeightDp: Int,
    val maximumMedianFrameMs: Double,
    val maximumTimerJitterP95Ms: Double,
) {
    init {
        require(minimumSdk > 0)
        require(minimumWidthDp > 0 && minimumHeightDp > 0)
        require(maximumMedianFrameMs > 0)
        require(maximumTimerJitterP95Ms > 0)
    }

    companion object {
        /**
         * Engineering-only candidate thresholds. Real BYOD evidence must freeze or
         * replace them before participant-facing qualification.
         */
        fun engineeringCandidate() = DeviceQualificationPolicy(
            minimumSdk = 26,
            minimumWidthDp = 320,
            minimumHeightDp = 480,
            maximumMedianFrameMs = 33.4,
            maximumTimerJitterP95Ms = 20.0,
        )
    }
}

data class DeviceQualificationEvidence(
    val sdkInt: Int,
    val widthDp: Int,
    val heightDp: Int,
    val touchInputVerified: Boolean,
    val monotonicClockVerified: Boolean,
    val durableStorageVerified: Boolean,
    val offlineCoreVerified: Boolean,
    val backgroundPauseVerified: Boolean,
    val medianFrameMs: Double?,
    val timerJitterP95Ms: Double?,
) {
    init {
        require(sdkInt > 0)
        require(widthDp > 0 && heightDp > 0)
        require(medianFrameMs == null || (medianFrameMs.isFinite() && medianFrameMs >= 0))
        require(timerJitterP95Ms == null || (timerJitterP95Ms.isFinite() && timerJitterP95Ms >= 0))
    }
}

data class DeviceQualificationDecision(
    val qualifiedCore: Boolean,
    val reasons: Set<String>,
)

object DeviceQualificationService {
    fun evaluate(
        policy: DeviceQualificationPolicy,
        evidence: DeviceQualificationEvidence,
    ): DeviceQualificationDecision {
        val reasons = linkedSetOf<String>()
        if (evidence.sdkInt < policy.minimumSdk) reasons += "sdk_below_minimum"
        if (evidence.widthDp < policy.minimumWidthDp) reasons += "width_below_minimum"
        if (evidence.heightDp < policy.minimumHeightDp) reasons += "height_below_minimum"
        if (!evidence.touchInputVerified) reasons += "touch_unverified"
        if (!evidence.monotonicClockVerified) reasons += "monotonic_clock_unverified"
        if (!evidence.durableStorageVerified) reasons += "durable_storage_unverified"
        if (!evidence.offlineCoreVerified) reasons += "offline_core_unverified"
        if (!evidence.backgroundPauseVerified) reasons += "background_pause_unverified"

        val frame = evidence.medianFrameMs
        if (frame == null) reasons += "frame_benchmark_missing"
        else if (frame > policy.maximumMedianFrameMs) reasons += "frame_budget_exceeded"

        val jitter = evidence.timerJitterP95Ms
        if (jitter == null) reasons += "timer_jitter_missing"
        else if (jitter > policy.maximumTimerJitterP95Ms) reasons += "timer_jitter_exceeded"

        return DeviceQualificationDecision(
            qualifiedCore = reasons.isEmpty(),
            reasons = reasons,
        )
    }
}
