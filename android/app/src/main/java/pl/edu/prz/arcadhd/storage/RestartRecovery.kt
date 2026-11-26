package pl.edu.prz.arcadhd.storage

import pl.edu.prz.arcadhd.session.SessionControls
import pl.edu.prz.arcadhd.session.SessionGraph

data class RestartPlan(val readOnly: Boolean, val reason: String, val command: SessionCommand? = null)

/** Engineering restart policy. Never silently continues an interrupted recovery measurement. */
object RestartRecovery {
    fun plan(stored: StoredSession, clockDomain: String, runtimeToken: String, nowMs: Long): RestartPlan {
        if (stored.clockDomain != clockDomain || nowMs < stored.snapshot.lastTimestampMs) {
            return RestartPlan(true, "clock_domain_changed")
        }
        if (stored.runtimeToken == runtimeToken || stored.snapshot.state in SessionGraph.terminal) {
            return RestartPlan(false, "checkpoint_restored")
        }
        val controls = when {
            stored.snapshot.recoveryContext != null -> SessionControls(protocolAbort = true)
            "technical_interrupt" in SessionGraph.transitions.getValue(stored.snapshot.state) ->
                SessionControls(technicalInterrupt = true)
            "pause" in SessionGraph.transitions.getValue(stored.snapshot.state) ->
                SessionControls(childPause = true)
            else -> return RestartPlan(false, "checkpoint_restored")
        }
        return RestartPlan(
            false,
            if (stored.snapshot.recoveryContext != null) "recovery_interrupted_protocol_abort"
            else "restart_requires_explicit_resume",
            SessionCommand(null, nowMs, controls, expectedSequence = stored.snapshot.sequence),
        )
    }
}
