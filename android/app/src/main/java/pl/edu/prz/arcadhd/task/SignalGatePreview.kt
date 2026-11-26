package pl.edu.prz.arcadhd.task

enum class GateColor { BLUE, ORANGE }
enum class GateRule { A, B }
enum class PreviewStage { READY, TASK, RULE_SWITCH, PAUSED, COMPLETE, STOPPED }

/** Immutable, untimed engineering preview. No research events or recovery scores. */
data class SignalGatePreview(
    val stage: PreviewStage = PreviewStage.READY,
    val trialIndex: Int = 0,
    val responses: List<PreviewResponse> = emptyList(),
    val resumeStage: PreviewStage? = null,
) {
    val activeRule: GateRule
        get() = if (trialIndex < SWITCH_TRIAL_INDEX) GateRule.A else GateRule.B
    val currentStimulus: GateColor?
        get() = SEQUENCE.getOrNull(trialIndex)
    val correctCount: Int
        get() = responses.count { it.correct }
    val isActive: Boolean
        get() = stage in setOf(PreviewStage.TASK, PreviewStage.RULE_SWITCH)

    /** Begin a fresh preview only from the ready screen. */
    fun start(): SignalGatePreview =
        if (stage == PreviewStage.READY) copy(stage = PreviewStage.TASK) else this

    /**
     * Accept one response for the displayed trial. A stale UI callback is ignored.
     * Withholding is an explicit developer action, not a measured NO-GO response.
     */
    fun submit(displayedTrialIndex: Int, tappedGo: Boolean): SignalGatePreview {
        if (stage != PreviewStage.TASK || displayedTrialIndex != trialIndex) return this
        val stimulus = currentStimulus ?: return this
        val expectedGo = when (activeRule) {
            GateRule.A -> stimulus == GateColor.BLUE
            GateRule.B -> stimulus == GateColor.ORANGE
        }
        val response = PreviewResponse(trialIndex, activeRule, stimulus, tappedGo, expectedGo)
        val nextIndex = trialIndex + 1
        val nextStage = when (nextIndex) {
            TRIAL_COUNT -> PreviewStage.COMPLETE
            SWITCH_TRIAL_INDEX -> PreviewStage.RULE_SWITCH
            else -> PreviewStage.TASK
        }
        return copy(stage = nextStage, trialIndex = nextIndex, responses = responses + response)
    }

    /** Require acknowledgement before the first Rule B stimulus can be answered. */
    fun acknowledgeSwitch(): SignalGatePreview =
        if (stage == PreviewStage.RULE_SWITCH) copy(stage = PreviewStage.TASK) else this

    /** Pause task or rule cue without generating an answer or advancing the sequence. */
    fun pause(): SignalGatePreview =
        if (isActive) copy(stage = PreviewStage.PAUSED, resumeStage = stage) else this

    /** Resumption always needs an explicit action, including after backgrounding. */
    fun resume(): SignalGatePreview =
        if (stage == PreviewStage.PAUSED && resumeStage != null) {
            copy(stage = resumeStage, resumeStage = null)
        } else this

    /** Stop takes precedence over progression. A stopped preview is terminal. */
    fun stop(): SignalGatePreview =
        if (stage in setOf(PreviewStage.COMPLETE, PreviewStage.STOPPED)) this
        else copy(stage = PreviewStage.STOPPED, resumeStage = null)

    companion object {
        const val SWITCH_TRIAL_INDEX = 4
        const val TRIAL_COUNT = 8
        private val SEQUENCE = listOf(
            GateColor.BLUE, GateColor.ORANGE, GateColor.ORANGE, GateColor.BLUE,
            GateColor.ORANGE, GateColor.BLUE, GateColor.BLUE, GateColor.ORANGE,
        )
    }
}

data class PreviewResponse(
    val trialIndex: Int,
    val rule: GateRule,
    val stimulus: GateColor,
    val tappedGo: Boolean,
    val expectedGo: Boolean,
) {
    val correct: Boolean get() = tappedGo == expectedGo
}
