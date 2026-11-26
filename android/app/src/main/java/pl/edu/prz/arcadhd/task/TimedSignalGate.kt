package pl.edu.prz.arcadhd.task

enum class TimedGateStage { READY, TASK, RULE_SWITCH, PAUSED, COMPLETE, STOPPED }
enum class TimedResponseKind { GO_TAP, WITHHOLD_TIMEOUT }

data class TimedGateTrial(
    val id: String,
    val index: Int,
    val rule: GateRule,
    val stimulus: GateColor,
    val shownAtMs: Long,
    val deadlineAtMs: Long,
) {
    init {
        require(id.isNotBlank())
        require(index >= 0)
        require(shownAtMs >= 0)
        require(deadlineAtMs > shownAtMs)
    }
}

data class TimedGateResponse(
    val trialId: String,
    val trialIndex: Int,
    val rule: GateRule,
    val stimulus: GateColor,
    val kind: TimedResponseKind,
    val expectedGo: Boolean,
    val correct: Boolean,
    val shownAtMs: Long,
    val respondedAtMs: Long,
) {
    val latencyMs: Long get() = respondedAtMs - shownAtMs

    init {
        require(trialId.isNotBlank())
        require(trialIndex >= 0)
        require(respondedAtMs >= shownAtMs)
    }
}

/**
 * Deterministic, monotonic-clock Signal Gate engine.
 *
 * This is engineering infrastructure. It records objective trial timing and never
 * decides whether a child is eligible for a study, recovered, or clinically improved.
 */
data class TimedSignalGate(
    val stage: TimedGateStage = TimedGateStage.READY,
    val trialIndex: Int = 0,
    val currentTrial: TimedGateTrial? = null,
    val responses: List<TimedGateResponse> = emptyList(),
    val resumeStage: TimedGateStage? = null,
    val pausedAtMs: Long? = null,
    val responseWindowMs: Long = DEFAULT_RESPONSE_WINDOW_MS,
    val runId: String = "engineering",
    val lastTimestampMs: Long = 0,
) {
    init {
        require(trialIndex in 0..TRIAL_COUNT)
        require(responseWindowMs > 0)
        require(runId.isNotBlank())
        require(lastTimestampMs >= 0)
    }

    val activeRule: GateRule
        get() = ruleFor(trialIndex)

    fun start(nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.READY || !validTime(nowMs)) return this
        return openTrial(trialIndex, nowMs)
    }

    fun submitGo(displayedTrialId: String, nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.TASK || !validTime(nowMs)) return this
        val trial = currentTrial ?: return this
        if (trial.id != displayedTrialId) return this
        if (nowMs >= trial.deadlineAtMs) return timeout(nowMs)
        return closeTrial(
            trial = trial,
            kind = TimedResponseKind.GO_TAP,
            respondedAtMs = nowMs,
        )
    }

    /** Expire the active trial at its deterministic deadline. */
    fun tick(nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.TASK || !validTime(nowMs)) return this
        val trial = currentTrial ?: return this
        return if (nowMs >= trial.deadlineAtMs) timeout(nowMs) else copy(lastTimestampMs = nowMs)
    }

    fun acknowledgeRuleSwitch(nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.RULE_SWITCH || !validTime(nowMs)) return this
        return openTrial(trialIndex, nowMs)
    }

    /**
     * Re-anchor an already prepared TASK trial at the moment it is actually presented.
     *
     * Durable-write-before-advance can delay UI presentation after the previous response.
     * Without this step, storage latency would be incorrectly counted as response time.
     */
    fun reanchorCurrentTrial(nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.TASK || !validTime(nowMs)) return this
        val trial = currentTrial ?: return this
        return copy(
            currentTrial = trial.copy(
                shownAtMs = nowMs,
                deadlineAtMs = nowMs + responseWindowMs,
            ),
            lastTimestampMs = nowMs,
        )
    }

    fun pause(nowMs: Long): TimedSignalGate {
        if (stage !in setOf(TimedGateStage.TASK, TimedGateStage.RULE_SWITCH) || !validTime(nowMs)) {
            return this
        }
        return copy(
            stage = TimedGateStage.PAUSED,
            resumeStage = stage,
            pausedAtMs = nowMs,
            lastTimestampMs = nowMs,
        )
    }

    /**
     * Paused wall-clock time is excluded from the active response window by shifting
     * the displayed trial timestamps on explicit resume.
     */
    fun resume(nowMs: Long): TimedSignalGate {
        if (stage != TimedGateStage.PAUSED || resumeStage == null || pausedAtMs == null ||
            !validTime(nowMs)
        ) {
            return this
        }
        val pauseDuration = nowMs - pausedAtMs
        val shifted = if (resumeStage == TimedGateStage.TASK) {
            currentTrial?.copy(
                shownAtMs = currentTrial.shownAtMs + pauseDuration,
                deadlineAtMs = currentTrial.deadlineAtMs + pauseDuration,
            )
        } else {
            currentTrial
        }
        return copy(
            stage = resumeStage,
            currentTrial = shifted,
            resumeStage = null,
            pausedAtMs = null,
            lastTimestampMs = nowMs,
        )
    }

    fun stop(nowMs: Long): TimedSignalGate {
        if (stage in setOf(TimedGateStage.COMPLETE, TimedGateStage.STOPPED) || !validTime(nowMs)) {
            return this
        }
        return copy(
            stage = TimedGateStage.STOPPED,
            currentTrial = null,
            resumeStage = null,
            pausedAtMs = null,
            lastTimestampMs = nowMs,
        )
    }

    private fun timeout(nowMs: Long): TimedSignalGate {
        val trial = currentTrial ?: return this
        return closeTrial(
            trial = trial,
            kind = TimedResponseKind.WITHHOLD_TIMEOUT,
            respondedAtMs = trial.deadlineAtMs,
        ).copy(lastTimestampMs = nowMs)
    }

    private fun closeTrial(
        trial: TimedGateTrial,
        kind: TimedResponseKind,
        respondedAtMs: Long,
    ): TimedSignalGate {
        val expectedGo = expectedGo(trial.rule, trial.stimulus)
        val tappedGo = kind == TimedResponseKind.GO_TAP
        val response = TimedGateResponse(
            trialId = trial.id,
            trialIndex = trial.index,
            rule = trial.rule,
            stimulus = trial.stimulus,
            kind = kind,
            expectedGo = expectedGo,
            correct = tappedGo == expectedGo,
            shownAtMs = trial.shownAtMs,
            respondedAtMs = respondedAtMs,
        )
        val nextIndex = trialIndex + 1
        val nextStage = when (nextIndex) {
            TRIAL_COUNT -> TimedGateStage.COMPLETE
            SWITCH_TRIAL_INDEX -> TimedGateStage.RULE_SWITCH
            else -> TimedGateStage.TASK
        }
        return if (nextStage == TimedGateStage.TASK) {
            openTrial(
                index = nextIndex,
                nowMs = respondedAtMs,
                priorResponses = responses + response,
            )
        } else {
            copy(
                stage = nextStage,
                trialIndex = nextIndex,
                currentTrial = null,
                responses = responses + response,
                lastTimestampMs = respondedAtMs,
            )
        }
    }

    private fun openTrial(
        index: Int,
        nowMs: Long,
        priorResponses: List<TimedGateResponse> = responses,
    ): TimedSignalGate {
        val stimulus = SEQUENCE.getOrNull(index) ?: return copy(
            stage = TimedGateStage.COMPLETE,
            trialIndex = TRIAL_COUNT,
            currentTrial = null,
            responses = priorResponses,
            lastTimestampMs = nowMs,
        )
        val trial = TimedGateTrial(
            id = "${runId}-t${index.toString().padStart(3, '0')}",
            index = index,
            rule = ruleFor(index),
            stimulus = stimulus,
            shownAtMs = nowMs,
            deadlineAtMs = nowMs + responseWindowMs,
        )
        return copy(
            stage = TimedGateStage.TASK,
            trialIndex = index,
            currentTrial = trial,
            responses = priorResponses,
            resumeStage = null,
            pausedAtMs = null,
            lastTimestampMs = nowMs,
        )
    }

    private fun validTime(nowMs: Long): Boolean = nowMs >= lastTimestampMs && nowMs >= 0

    companion object {
        const val SWITCH_TRIAL_INDEX = 4
        const val TRIAL_COUNT = 8
        const val DEFAULT_RESPONSE_WINDOW_MS = 1500L

        private val SEQUENCE = listOf(
            GateColor.BLUE, GateColor.ORANGE, GateColor.ORANGE, GateColor.BLUE,
            GateColor.ORANGE, GateColor.BLUE, GateColor.BLUE, GateColor.ORANGE,
        )

        private fun ruleFor(index: Int): GateRule =
            if (index < SWITCH_TRIAL_INDEX) GateRule.A else GateRule.B

        private fun expectedGo(rule: GateRule, stimulus: GateColor): Boolean = when (rule) {
            GateRule.A -> stimulus == GateColor.BLUE
            GateRule.B -> stimulus == GateColor.ORANGE
        }
    }
}
