package pl.edu.prz.arcadhd.platform.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import pl.edu.prz.arcadhd.measurement.ChallengeEligibilityDecision
import pl.edu.prz.arcadhd.measurement.RecoveryMonitorConfig
import pl.edu.prz.arcadhd.session.CoreCycleCoordinator
import pl.edu.prz.arcadhd.session.CoreCycleDecision
import pl.edu.prz.arcadhd.session.CoreCycleSnapshot
import pl.edu.prz.arcadhd.session.RecoveryContext
import pl.edu.prz.arcadhd.storage.SessionCommand
import pl.edu.prz.arcadhd.session.SessionControls
import pl.edu.prz.arcadhd.session.SessionMode
import pl.edu.prz.arcadhd.session.SessionSnapshot
import pl.edu.prz.arcadhd.session.SessionState
import pl.edu.prz.arcadhd.storage.EngineeringTrialRecord
import pl.edu.prz.arcadhd.storage.JournalIntegrityException
import pl.edu.prz.arcadhd.storage.RestartRecovery
import pl.edu.prz.arcadhd.storage.SessionJournal
import pl.edu.prz.arcadhd.storage.StoredSession
import pl.edu.prz.arcadhd.task.TimedGateResponse

data class JournalView(
    val stored: StoredSession?,
    val readOnly: Boolean = false,
    val message: String = "",
)

data class TrialWriteView(
    val accepted: Boolean,
    val storedCount: Int = 0,
    val message: String = "",
)

enum class EngineeringCoreAction {
    CHALLENGE_DELIVERED,
    PAUSE_COMPLETE,
    ORIENTATION_COMPLETE,
    STRATEGY_SELECTED,
    STRATEGY_COMPLETE_WITHOUT_REFERENCE,
    CONTINUE_AFTER_INVALID_RECOVERY,
    SESSION_GOAL_COMPLETE,
    REFLECTION_COMPLETE,
}

/** One process-wide IO queue prevents Activity recreation from racing database commits. */
object EngineeringJournalRuntime {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val runtimeToken = UUID.randomUUID().toString()
    private var journal: SessionJournal? = null
    private var clockDomain: String? = null

    fun initialize(context: Context, callback: (JournalView) -> Unit) = submit(callback) {
        if (journal == null) {
            val app = context.applicationContext
            val db = AndroidJournalDatabase(app).writableDatabase
            val store = SessionJournal(AndroidJournalSql(db))
            val domain = clockDomain(app)
            store.recoverInFlight()
            journal = store
            clockDomain = domain
        }
        val store = requireNotNull(journal)
        var stored = store.active() ?: return@submit JournalView(null)
        val plan = RestartRecovery.plan(
            stored, requireNotNull(clockDomain), runtimeToken, SystemClock.elapsedRealtime(),
        )
        if (plan.command != null) {
            val decision = store.append(stored.sessionId, plan.command)
            check(decision.rejection == null)
            stored = store.load(stored.sessionId)
        }
        if (!plan.readOnly) store.bindRuntime(stored.sessionId, runtimeToken)
        JournalView(store.load(stored.sessionId), plan.readOnly, plan.reason)
    }

    fun create(mode: SessionMode, callback: (JournalView) -> Unit) = submit(callback) {
        val stored = requireNotNull(journal).create(mode, requireNotNull(clockDomain), runtimeToken)
        JournalView(stored, message = "new_engineering_session")
    }

    /**
     * Move a fresh engineering Core session to BASELINE_TASK.
     *
     * These setup transitions are developer scaffolding. In particular,
     * qualified_with_fallbacks is not evidence that a physical phone passed BYOD qualification.
     */
    fun prepareCoreTask(sessionId: String, callback: (JournalView) -> Unit) = submit(callback) {
        val store = requireNotNull(journal)
        var stored = store.load(sessionId)
        if (stored.clockDomain != clockDomain) {
            return@submit JournalView(stored, readOnly = true, message = "clock_domain_changed")
        }
        if (stored.snapshot.state == SessionState.BASELINE_TASK) {
            return@submit JournalView(stored, message = "engineering_core_ready")
        }
        if (stored.snapshot.state != SessionState.BOOT) {
            return@submit JournalView(stored, readOnly = true, message = "core_task_requires_fresh_session")
        }

        val bootstrap = listOf(
            "initialized",
            "configuration_loaded",
            "qualified_with_fallbacks",
            "onboarding_complete",
            "ready_without_tutorial",
            "calibration_complete",
        )
        bootstrap.forEach { event ->
            val current = stored.snapshot
            val timestamp = maxOf(SystemClock.elapsedRealtime(), current.lastTimestampMs)
            val decision = store.append(
                sessionId,
                SessionCommand(
                    event = event,
                    timestampMs = timestamp,
                    expectedSequence = current.sequence,
                ),
            )
            if (decision.rejection != null) {
                return@submit JournalView(
                    store.load(sessionId),
                    readOnly = true,
                    message = decision.rejection,
                )
            }
            stored = store.load(sessionId)
        }
        if (stored.snapshot.state != SessionState.BASELINE_TASK) {
            throw JournalIntegrityException("core_plan_mismatch")
        }
        JournalView(stored, message = "engineering_core_ready")
    }

    fun input(
        sessionId: String,
        expectedSequence: Long,
        event: String?,
        controls: SessionControls,
        callback: (JournalView) -> Unit,
    ) = submit(callback) {
        val store = requireNotNull(journal)
        val current = store.load(sessionId)
        if (current.clockDomain != clockDomain) return@submit JournalView(
            current, readOnly = true, message = "clock_domain_changed",
        )
        val command = SessionCommand(
            event,
            SystemClock.elapsedRealtime(),
            controls,
            current.snapshot.recoveryContext ?: engineeringRecoveryContext(),
            if (controls != SessionControls()) current.snapshot.sequence else expectedSequence,
        )
        if (controls == SessionControls() && expectedSequence != current.snapshot.sequence) {
            return@submit JournalView(current, message = "stale_sequence")
        }
        val decision = store.append(sessionId, command)
        JournalView(
            store.load(sessionId),
            message = decision.rejection ?: decision.transition?.reason ?: "identical_retry",
        )
    }

    fun adjudicateChallenge(
        sessionId: String,
        eligibility: ChallengeEligibilityDecision,
        callback: (JournalView) -> Unit,
    ) = submit(callback) {
        val store = requireNotNull(journal)
        val current = writableSession(store, sessionId) ?: return@submit JournalView(
            store.load(sessionId), readOnly = true, message = "clock_domain_changed",
        )
        val timestamp = maxOf(SystemClock.elapsedRealtime(), current.snapshot.lastTimestampMs)
        val expected = CoreCycleCoordinator.adjudicateChallenge(
            CoreCycleSnapshot(current.snapshot),
            eligibility,
            timestamp,
        )
        if (expected.rejection != null) {
            return@submit JournalView(current, message = expected.rejection)
        }
        persistCorePlan(
            store,
            current,
            expected,
            listOf(
                PlannedCoreEvent("challenge_candidate"),
                PlannedCoreEvent(if (eligibility.eligible) "eligible" else "suppressed"),
            ),
        )
        JournalView(store.load(sessionId), message = if (eligibility.eligible) {
            "engineering_challenge_started"
        } else {
            "engineering_challenge_suppressed"
        })
    }

    fun coreAction(
        sessionId: String,
        action: EngineeringCoreAction,
        callback: (JournalView) -> Unit,
    ) = submit(callback) {
        val store = requireNotNull(journal)
        val current = writableSession(store, sessionId) ?: return@submit JournalView(
            store.load(sessionId), readOnly = true, message = "clock_domain_changed",
        )
        val timestamp = maxOf(SystemClock.elapsedRealtime(), current.snapshot.lastTimestampMs)
        val core = CoreCycleSnapshot(current.snapshot)
        val context = engineeringRecoveryContext()

        val plan = when (action) {
            EngineeringCoreAction.CHALLENGE_DELIVERED -> CorePlan(
                CoreCycleCoordinator.challengeDelivered(core, timestamp),
                listOf(PlannedCoreEvent("challenge_delivered")),
            )
            EngineeringCoreAction.PAUSE_COMPLETE -> CorePlan(
                CoreCycleCoordinator.pauseComplete(core, timestamp),
                listOf(PlannedCoreEvent("pause_complete")),
            )
            EngineeringCoreAction.ORIENTATION_COMPLETE -> CorePlan(
                CoreCycleCoordinator.orientationComplete(core, timestamp),
                listOf(PlannedCoreEvent("orientation_complete")),
            )
            EngineeringCoreAction.STRATEGY_SELECTED -> CorePlan(
                CoreCycleCoordinator.strategySelected(core, timestamp),
                listOf(PlannedCoreEvent("strategy_selected")),
            )
            EngineeringCoreAction.STRATEGY_COMPLETE_WITHOUT_REFERENCE -> {
                val monitoring = CoreCycleCoordinator.strategyComplete(
                    core,
                    context,
                    RecoveryMonitorConfig.phase0Synthetic(),
                    timestamp,
                )
                if (monitoring.rejection != null) {
                    CorePlan(monitoring, emptyList())
                } else {
                    val invalid = CoreCycleCoordinator.invalidateRecovery(
                        monitoring.snapshot,
                        "engineering_target_rule_reference_not_calibrated",
                        timestamp,
                    )
                    CorePlan(
                        CoreCycleDecision(
                            snapshot = invalid.snapshot,
                            transitions = monitoring.transitions + invalid.transitions,
                            rejection = invalid.rejection,
                        ),
                        listOf(
                            PlannedCoreEvent("strategy_complete", context),
                            PlannedCoreEvent("required_data_invalid"),
                        ),
                    )
                }
            }
            EngineeringCoreAction.CONTINUE_AFTER_INVALID_RECOVERY -> {
                val continuation = CoreCycleCoordinator.continuationAllowed(core, timestamp)
                if (continuation.rejection != null) {
                    CorePlan(continuation, emptyList())
                } else {
                    val resumed = CoreCycleCoordinator.resumeGoal(continuation.snapshot, timestamp)
                    CorePlan(
                        CoreCycleDecision(
                            snapshot = resumed.snapshot,
                            transitions = continuation.transitions + resumed.transitions,
                            rejection = resumed.rejection,
                        ),
                        listOf(
                            PlannedCoreEvent("continuation_allowed"),
                            PlannedCoreEvent("continue_task"),
                        ),
                    )
                }
            }
            EngineeringCoreAction.SESSION_GOAL_COMPLETE -> CorePlan(
                CoreCycleCoordinator.sessionGoalComplete(core, timestamp),
                listOf(PlannedCoreEvent("session_goal_complete")),
            )
            EngineeringCoreAction.REFLECTION_COMPLETE -> CorePlan(
                CoreCycleCoordinator.reflectionComplete(core, timestamp),
                listOf(PlannedCoreEvent("reflection_complete")),
            )
        }

        if (plan.decision.rejection != null) {
            return@submit JournalView(current, message = plan.decision.rejection)
        }
        persistCorePlan(store, current, plan.decision, plan.events)
        JournalView(store.load(sessionId), message = action.name.lowercase())
    }

    fun appendTrial(
        sessionId: String,
        taskRunId: String,
        response: TimedGateResponse,
        callback: (TrialWriteView) -> Unit,
    ) {
        io.execute {
            val result = try {
                val store = requireNotNull(journal)
                val stored = store.load(sessionId)
                if (stored.clockDomain != clockDomain) {
                    TrialWriteView(false, message = "clock_domain_changed")
                } else {
                    val record = EngineeringTrialRecord(
                        taskRunId = taskRunId,
                        trialId = response.trialId,
                        trialIndex = response.trialIndex,
                        ruleId = response.rule.name,
                        stimulusId = response.stimulus.name,
                        responseKind = response.kind.name,
                        expectedGo = response.expectedGo,
                        correct = response.correct,
                        shownAtMs = response.shownAtMs,
                        respondedAtMs = response.respondedAtMs,
                    )
                    store.appendTrial(sessionId, record)
                    val count = store.trials(sessionId, taskRunId).size
                    TrialWriteView(true, count, "trial_persisted")
                }
            } catch (error: JournalIntegrityException) {
                TrialWriteView(false, message = error.code)
            } catch (_: SQLiteException) {
                TrialWriteView(false, message = "local_storage_failure")
            } catch (_: JournalStorageException) {
                TrialWriteView(false, message = "local_storage_failure")
            } catch (_: IllegalArgumentException) {
                TrialWriteView(false, message = "trial_decode_failure")
            }
            main.post { callback(result) }
        }
    }

    private fun writableSession(store: SessionJournal, sessionId: String): StoredSession? {
        val current = store.load(sessionId)
        return if (current.clockDomain == clockDomain) current else null
    }

    private data class PlannedCoreEvent(
        val event: String,
        val context: RecoveryContext? = null,
    )

    private data class CorePlan(
        val decision: CoreCycleDecision,
        val events: List<PlannedCoreEvent>,
    )

    private fun persistCorePlan(
        store: SessionJournal,
        initial: StoredSession,
        expected: CoreCycleDecision,
        events: List<PlannedCoreEvent>,
    ) {
        if (events.size != expected.transitions.size) {
            throw JournalIntegrityException("core_plan_mismatch")
        }
        var snapshot: SessionSnapshot = initial.snapshot
        events.forEachIndexed { index, planned ->
            val transition = expected.transitions[index]
            if (transition.reason != planned.event) {
                throw JournalIntegrityException("core_plan_mismatch")
            }
            val decision = store.append(
                initial.sessionId,
                SessionCommand(
                    event = planned.event,
                    timestampMs = transition.timestampMs,
                    context = planned.context,
                    expectedSequence = snapshot.sequence,
                ),
            )
            if (decision.rejection != null || decision.transition == null) {
                throw JournalIntegrityException("core_plan_persistence_failure")
            }
            snapshot = decision.snapshot
        }
        if (snapshot != expected.snapshot.session) {
            throw JournalIntegrityException("core_plan_mismatch")
        }
    }

    private fun engineeringRecoveryContext() = RecoveryContext(
        taskRuleId = "B",
        cognitiveDifficulty = 1,
        sensoryLoad = 0,
        responseWindowMs = 1500,
        stimulusDistributionId = "signal-gate-engineering-v2",
        cueCondition = "visual-only",
    )

    private fun submit(callback: (JournalView) -> Unit, work: () -> JournalView) {
        io.execute {
            val result = try {
                work()
            } catch (error: JournalIntegrityException) {
                JournalView(null, true, error.code)
            } catch (_: SQLiteException) {
                JournalView(null, true, "local_storage_failure")
            } catch (_: JournalStorageException) {
                JournalView(null, true, "local_storage_failure")
            } catch (_: IOException) {
                JournalView(null, true, "journal_decode_failure")
            } catch (_: IllegalArgumentException) {
                JournalView(null, true, "journal_decode_failure")
            }
            main.post { callback(result) }
        }
    }

    /** Store only an app-generated clock token; reboot count is local and is never a research identifier. */
    private fun clockDomain(context: Context): String {
        val bootCount = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: SecurityException) {
            -1
        }
        val prefs = context.getSharedPreferences("engineering-clock-domain", Context.MODE_PRIVATE)
        if (bootCount >= 0 && prefs.getInt("boot_count", -2) == bootCount) {
            prefs.getString("token", null)?.let { return it }
        }
        val token = UUID.randomUUID().toString()
        if (!prefs.edit().putInt("boot_count", bootCount).putString("token", token).commit()) {
            throw JournalStorageException()
        }
        return token
    }
}
