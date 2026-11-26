package pl.edu.prz.arcadhd.session

import java.security.MessageDigest

enum class SessionMode { CORE, ACTIVE_CONTROL }

/** Inputs already adjudicated by the safety/protocol services, not inferred from performance. */
data class SessionControls(
    val fatalIntegrity: Boolean = false,
    val safetyStop: Boolean = false,
    val childStop: Boolean = false,
    val childPause: Boolean = false,
    val protocolAbort: Boolean = false,
    val technicalInterrupt: Boolean = false,
)

/** Score-relevant context only. Values must come from a versioned study configuration. */
data class RecoveryContext(
    val taskRuleId: String,
    val cognitiveDifficulty: Int,
    val sensoryLoad: Int,
    val responseWindowMs: Int,
    val stimulusDistributionId: String,
    val cueCondition: String,
) {
    init {
        require(taskRuleId.isNotBlank() && stimulusDistributionId.isNotBlank())
        require(cueCondition.isNotBlank())
        require(cognitiveDifficulty >= 0 && sensoryLoad >= 0 && responseWindowMs > 0)
    }

    /** Length-prefixed UTF-8 encoding makes the deterministic hash unambiguous. */
    fun hash(): String {
        val fields = listOf(
            taskRuleId, cognitiveDifficulty.toString(), sensoryLoad.toString(),
            responseWindowMs.toString(), stimulusDistributionId, cueCondition,
        )
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

data class SessionSnapshot(
    val state: SessionState = SessionGraph.initial,
    val mode: SessionMode = SessionMode.CORE,
    val sequence: Long = 0,
    val lastTimestampMs: Long = 0,
    val resumeTarget: SessionState? = null,
    val recoveryContext: RecoveryContext? = null,
) {
    init {
        require(sequence >= 0 && lastTimestampMs >= 0)
    }
}

/** This record is returned to the caller; it is not yet durably persisted telemetry. */
data class SessionTransition(
    val sequence: Long,
    val timestampMs: Long,
    val from: SessionState,
    val to: SessionState,
    val reason: String,
    val configurationHash: String = SessionGraph.SOURCE_SHA256,
    val recoveryContextHash: String?,
)

data class SessionDecision(
    val snapshot: SessionSnapshot,
    val transition: SessionTransition? = null,
    val rejection: String? = null,
)

/**
 * Pure Kotlin reducer for the committed session graph. Call on the current snapshot.
 * It validates transitions, not task comprehension, eligibility or recovery criteria.
 */
object SessionController {
    private val controlForbidden = setOf(
        SessionState.CHALLENGE_PENDING, SessionState.CHALLENGE_ACTIVE,
        SessionState.REGULATION_PAUSE, SessionState.ORIENT,
        SessionState.STRATEGY_SELECTION, SessionState.STRATEGY_EXECUTION,
        SessionState.RECOVERY_MONITORING, SessionState.RECOVERY_OBSERVED,
        SessionState.RECOVERY_CENSORED, SessionState.RECOVERY_DATA_INVALID,
        SessionState.RESUME_GOAL,
    )

    /**
     * Apply the highest-priority control or a named graph event at monotonic milliseconds.
     * Supply context on entry to recovery and when confirming a valid recovery resume.
     * Expected sequence rejects stale ordinary inputs; global controls use current state.
     */
    fun reduce(
        snapshot: SessionSnapshot,
        event: String? = null,
        timestampMs: Long,
        controls: SessionControls = SessionControls(),
        context: RecoveryContext? = null,
        expectedSequence: Long = snapshot.sequence,
    ): SessionDecision {
        fun reject(reason: String) = SessionDecision(snapshot, rejection = reason)
        if (timestampMs < 0 || timestampMs < snapshot.lastTimestampMs) {
            return reject("non_monotonic_timestamp")
        }
        if (snapshot.state in SessionGraph.terminal) return reject("terminal_state")
        if (snapshot.sequence == Long.MAX_VALUE) return reject("sequence_exhausted")

        val reason = when {
            controls.fatalIntegrity -> "fatal_integrity_condition"
            controls.safetyStop -> "safety_stop"
            controls.childStop -> "explicit_child_stop"
            controls.childPause -> "pause"
            controls.protocolAbort -> "protocol_abort"
            controls.technicalInterrupt -> "technical_interrupt"
            else -> event ?: return reject("missing_event")
        }
        val hasControl = controls != SessionControls()
        if (!hasControl && expectedSequence != snapshot.sequence) {
            return reject("stale_sequence")
        }
        val edges = SessionGraph.transitions.getValue(snapshot.state)
        val globalTarget = when {
            controls.fatalIntegrity -> SessionState.FATAL_ERROR
            controls.safetyStop || controls.childStop -> SessionState.SAFE_STOP
            controls.protocolAbort && !controls.childPause -> SessionState.PROTOCOL_ABORT
            else -> null
        }
        if (globalTarget == null && !edges.containsKey(reason)) {
            return reject("event_not_allowed")
        }
        val target = globalTarget ?: edges[reason] ?: snapshot.resumeTarget
            ?: return reject("missing_resume_target")
        if (globalTarget == null && edges[reason] == null) {
            val returnEvent = if (snapshot.state == SessionState.PAUSED_BY_CHILD) {
                "pause"
            } else "technical_interrupt"
            if (SessionGraph.transitions.getValue(target)[returnEvent] != snapshot.state) {
                return reject("invalid_resume_target")
            }
        }
        if (snapshot.mode == SessionMode.ACTIVE_CONTROL && target in controlForbidden) {
            return reject("active_control_boundary")
        }
        if (reason == "recovery_invalidated" && snapshot.recoveryContext == null) {
            return reject("no_recovery_to_invalidate")
        }
        var lockedContext = snapshot.recoveryContext
        if (target == SessionState.RECOVERY_MONITORING) {
            if (snapshot.state in setOf(SessionState.PAUSED_BY_CHILD, SessionState.TECHNICAL_INTERRUPT)) {
                if (lockedContext == null || context != lockedContext) {
                    return reject("recovery_resume_context_unverified")
                }
            } else {
                if (context == null) return reject("recovery_context_required")
                lockedContext = context
            }
        }
        if (snapshot.state == SessionState.RECOVERY_MONITORING &&
            target in setOf(SessionState.RECOVERY_OBSERVED, SessionState.RECOVERY_CENSORED) &&
            (lockedContext == null || context != lockedContext)
        ) {
            return reject("recovery_outcome_context_unverified")
        }
        val nextResume = when (target) {
            SessionState.PAUSED_BY_CHILD, SessionState.TECHNICAL_INTERRUPT -> snapshot.state
            else -> null
        }
        val record = SessionTransition(
            sequence = snapshot.sequence + 1,
            timestampMs = timestampMs,
            from = snapshot.state,
            to = target,
            reason = reason,
            recoveryContextHash = lockedContext?.hash(),
        )
        // Retain context through pause, interruption and recovery closure.
        // Clear only on return to ordinary progression or terminal session exit.
        if (target == SessionState.RESUME_GOAL || target in SessionGraph.terminal) {
            lockedContext = null
        }
        val next = snapshot.copy(
            state = target,
            sequence = record.sequence,
            lastTimestampMs = timestampMs,
            resumeTarget = nextResume,
            recoveryContext = lockedContext,
        )
        return SessionDecision(next, transition = record)
    }
}
