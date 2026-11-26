package pl.edu.prz.arcadhd

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.util.UUID
import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.StudyVariant
import pl.edu.prz.arcadhd.onboarding.ConsentChoices
import pl.edu.prz.arcadhd.onboarding.DemoAgreement
import pl.edu.prz.arcadhd.onboarding.ProcessDemoAgreement
import pl.edu.prz.arcadhd.platform.storage.EngineeringCoreAction
import pl.edu.prz.arcadhd.platform.storage.EngineeringJournalRuntime
import pl.edu.prz.arcadhd.platform.storage.JournalView
import pl.edu.prz.arcadhd.session.SessionControls
import pl.edu.prz.arcadhd.session.SessionGraph
import pl.edu.prz.arcadhd.session.SessionMode
import pl.edu.prz.arcadhd.session.SessionSnapshot
import pl.edu.prz.arcadhd.session.SessionState
import pl.edu.prz.arcadhd.task.EngineeringSignalGatePolicy
import pl.edu.prz.arcadhd.task.TimedGateStage
import pl.edu.prz.arcadhd.task.TimedSignalGate
import pl.edu.prz.arcadhd.ui.AgreementScreen
import pl.edu.prz.arcadhd.ui.ArcTheme
import pl.edu.prz.arcadhd.ui.HomeScreen
import pl.edu.prz.arcadhd.ui.InstructionsScreen
import pl.edu.prz.arcadhd.ui.SessionPreviewScreen
import pl.edu.prz.arcadhd.ui.TimedCoreSignalGateScreen
import pl.edu.prz.arcadhd.variant.CurrentArticleModule

class MainActivity : ComponentActivity() {
    private var timedGate by mutableStateOf(TimedSignalGate())
    private var showTimedTask by mutableStateOf(false)
    private var taskRunId by mutableStateOf<String?>(null)
    private var taskFeedback by mutableStateOf("")
    private var taskWriteBlocked by mutableStateOf(false)
    private var selectedStrategy by mutableStateOf<String?>(null)
    private var stopRequested = false
    private var activityInForeground = true

    private var session by mutableStateOf(SessionSnapshot())
    private var showSession by mutableStateOf(false)
    private var sessionFeedback by mutableStateOf("")

    private var sessionId by mutableStateOf<String?>(null)
    private var journalReadOnly by mutableStateOf(true)
    private var journalReady by mutableStateOf(false)
    private var storageHealthy by mutableStateOf(false)
    private var pendingOperations by mutableStateOf(0)
    private var generation = 0L
    private var agreement by mutableStateOf(ProcessDemoAgreement.current(CurrentArticleModule.paperId))
    private var informationPage by mutableStateOf<String?>(null)
    private var instructionsReturn by mutableStateOf<String?>(null)

    private fun allowed() = ProcessDemoAgreement.current(CurrentArticleModule.paperId) != null

    private fun initializeJournal() {
        if (!allowed()) return
        val requestGeneration = generation
        pendingOperations++
        EngineeringJournalRuntime.initialize(applicationContext) { view ->
            pendingOperations--
            if (generation == requestGeneration && allowed()) {
                showJournal(view)
                journalReady = true
            }
        }
    }

    private fun acceptAgreement(choices: ConsentChoices) {
        if (!ProcessDemoAgreement.accept(choices, CurrentArticleModule.paperId)) return
        agreement = ProcessDemoAgreement.current(CurrentArticleModule.paperId)
        informationPage = null
        initializeJournal()
    }

    private fun withdrawAgreement() {
        if (session.state !in SessionGraph.terminal) {
            applySessionInput(null, SessionControls(childStop = true))
        }
        timedGate = timedGate.stop(SystemClock.elapsedRealtime())
        ProcessDemoAgreement.withdraw()
        agreement = null
        generation++
        showSession = false
        showTimedTask = false
        informationPage = null
        journalReady = false
        taskRunId = null
        selectedStrategy = null
    }

    private fun showJournal(view: JournalView) {
        journalReadOnly = view.readOnly
        sessionFeedback = view.message
        storageHealthy = view.stored != null || !view.readOnly
        view.stored?.let {
            sessionId = it.sessionId
            session = it.snapshot
        }
    }

    private fun createSession(mode: SessionMode) {
        if (!allowed() || !journalReady || !storageHealthy || pendingOperations > 0) return
        val requestGeneration = ++generation
        pendingOperations++
        EngineeringJournalRuntime.create(mode) { view ->
            pendingOperations--
            if (generation == requestGeneration) {
                showJournal(view)
                if (view.stored != null && allowed()) showSession = true
            }
        }
    }

    private fun startTimedCoreDemo() {
        if (!allowed() || !journalReady || !storageHealthy || pendingOperations > 0) return
        val requestGeneration = ++generation
        taskWriteBlocked = false
        taskFeedback = ""
        selectedStrategy = null
        stopRequested = false
        pendingOperations++

        EngineeringJournalRuntime.create(SessionMode.CORE) { created ->
            pendingOperations--
            if (generation != requestGeneration || !allowed()) return@create
            showJournal(created)
            val stored = created.stored
            if (stored == null || created.readOnly) {
                taskWriteBlocked = true
                taskFeedback = created.message.ifBlank { "local_storage_failure" }
                return@create
            }

            pendingOperations++
            EngineeringJournalRuntime.prepareCoreTask(stored.sessionId) { ready ->
                pendingOperations--
                if (generation != requestGeneration || !allowed()) return@prepareCoreTask
                showJournal(ready)
                val prepared = ready.stored
                if (prepared == null || ready.readOnly ||
                    prepared.snapshot.state != SessionState.BASELINE_TASK
                ) {
                    taskWriteBlocked = true
                    taskFeedback = ready.message.ifBlank { "core_plan_mismatch" }
                    return@prepareCoreTask
                }

                val runId = UUID.randomUUID().toString()
                taskRunId = runId
                timedGate = TimedSignalGate(
                    runId = runId,
                    responseWindowMs = TimedSignalGate.DEFAULT_RESPONSE_WINDOW_MS,
                )
                taskFeedback = "engineering_core_ready"
                showSession = false
                showTimedTask = true
            }
        }
    }

    private fun applySessionInput(event: String?, controls: SessionControls) {
        val id = sessionId ?: return
        if (!allowed() || !journalReady || journalReadOnly ||
            (pendingOperations > 0 && controls == SessionControls())
        ) {
            return
        }
        val requestGeneration = generation
        pendingOperations++
        EngineeringJournalRuntime.input(id, session.sequence, event, controls) { view ->
            pendingOperations--
            if (requestGeneration == generation && id == sessionId) showJournal(view)
        }
    }

    private fun persistTimedResponse(next: TimedSignalGate) {
        val id = sessionId ?: return
        val runId = taskRunId ?: return
        if (pendingOperations > 0 || taskWriteBlocked || next.responses.size != timedGate.responses.size + 1) {
            return
        }
        val response = next.responses.last()
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.appendTrial(id, runId, response) { result ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId || !showTimedTask) return@appendTrial
            taskFeedback = result.message
            if (!result.accepted || result.storedCount != next.responses.size) {
                taskWriteBlocked = true
                return@appendTrial
            }

            val committedNext = if (next.stage == TimedGateStage.TASK) {
                next.reanchorCurrentTrial(SystemClock.elapsedRealtime())
            } else {
                next
            }
            timedGate = committedNext
            when (committedNext.stage) {
                TimedGateStage.RULE_SWITCH -> beginEngineeringChallenge()
                TimedGateStage.COMPLETE -> runCoreAction(
                    EngineeringCoreAction.SESSION_GOAL_COMPLETE,
                    expectedState = SessionState.SESSION_REFLECTION,
                )
                TimedGateStage.TASK -> {
                    if (!activityInForeground) pauseTimedTask()
                }
                else -> Unit
            }
        }
    }

    private fun handleTimedGo(displayedTrialId: String) {
        if (pendingOperations > 0 || taskWriteBlocked) return
        val next = timedGate.submitGo(displayedTrialId, SystemClock.elapsedRealtime())
        if (next.responses.size > timedGate.responses.size) persistTimedResponse(next)
    }

    private fun handleTimedTimeout() {
        if (pendingOperations > 0 || taskWriteBlocked) return
        val next = timedGate.tick(SystemClock.elapsedRealtime())
        if (next.responses.size > timedGate.responses.size) persistTimedResponse(next)
    }

    private fun beginEngineeringChallenge() {
        val id = sessionId ?: return
        if (pendingOperations > 0 || taskWriteBlocked) return
        val decision = EngineeringSignalGatePolicy.challengeDecision(
            timedGate.responses,
            SystemClock.elapsedRealtime(),
        )
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.adjudicateChallenge(id, decision) { view ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId || !showTimedTask) {
                return@adjudicateChallenge
            }
            showJournal(view)
            taskFeedback = view.message
            if (view.stored == null || view.readOnly) {
                taskWriteBlocked = true
                return@adjudicateChallenge
            }
            if (!decision.eligible && session.state == SessionState.BASELINE_TASK) {
                timedGate = timedGate.acknowledgeRuleSwitch(SystemClock.elapsedRealtime())
            } else if (session.state != SessionState.CHALLENGE_ACTIVE) {
                taskWriteBlocked = true
            }
        }
    }

    private fun runCoreAction(
        action: EngineeringCoreAction,
        expectedState: SessionState,
        onSuccess: (() -> Unit)? = null,
    ) {
        val id = sessionId ?: return
        if (pendingOperations > 0 || taskWriteBlocked) return
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.coreAction(id, action) { view ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId || !showTimedTask) return@coreAction
            showJournal(view)
            taskFeedback = view.message
            if (view.stored == null || view.readOnly || session.state != expectedState) {
                taskWriteBlocked = true
                return@coreAction
            }
            onSuccess?.invoke()
        }
    }

    private fun selectStrategy(strategy: String) {
        runCoreAction(
            EngineeringCoreAction.STRATEGY_SELECTED,
            expectedState = SessionState.STRATEGY_EXECUTION,
        ) {
            selectedStrategy = strategy
        }
    }

    private fun continueAfterInvalidRecovery() {
        runCoreAction(
            EngineeringCoreAction.CONTINUE_AFTER_INVALID_RECOVERY,
            expectedState = SessionState.BASELINE_TASK,
        ) {
            val resumed = timedGate.acknowledgeRuleSwitch(SystemClock.elapsedRealtime())
            if (resumed.stage != TimedGateStage.TASK) {
                taskWriteBlocked = true
            } else {
                timedGate = resumed
            }
        }
    }

    private fun pauseTimedTask() {
        val id = sessionId ?: return
        if (pendingOperations > 0 || taskWriteBlocked ||
            session.state != SessionState.BASELINE_TASK ||
            timedGate.stage != TimedGateStage.TASK
        ) {
            return
        }
        val next = timedGate.pause(SystemClock.elapsedRealtime())
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.input(
            id,
            session.sequence,
            null,
            SessionControls(childPause = true),
        ) { view ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId || !showTimedTask) return@input
            showJournal(view)
            taskFeedback = view.message
            if (view.stored == null || view.readOnly || session.state != SessionState.PAUSED_BY_CHILD) {
                taskWriteBlocked = true
            } else {
                timedGate = next
            }
        }
    }

    private fun resumeTimedTask() {
        val id = sessionId ?: return
        if (pendingOperations > 0 || taskWriteBlocked ||
            session.state != SessionState.PAUSED_BY_CHILD ||
            timedGate.stage != TimedGateStage.PAUSED
        ) {
            return
        }
        val next = timedGate.resume(SystemClock.elapsedRealtime())
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.input(
            id,
            session.sequence,
            "resume",
            SessionControls(),
        ) { view ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId || !showTimedTask) return@input
            showJournal(view)
            taskFeedback = view.message
            if (view.stored == null || view.readOnly || session.state != SessionState.BASELINE_TASK) {
                taskWriteBlocked = true
            } else {
                timedGate = next
            }
        }
    }

    private fun stopTimedTask(hideAfter: Boolean = false) {
        if (stopRequested) return
        val id = sessionId
        if (id == null || session.state in SessionGraph.terminal) {
            timedGate = timedGate.stop(SystemClock.elapsedRealtime())
            if (hideAfter) showTimedTask = false
            return
        }
        stopRequested = true
        val requestGeneration = generation
        pendingOperations++

        EngineeringJournalRuntime.input(
            id,
            session.sequence,
            null,
            SessionControls(childStop = true),
        ) { view ->
            pendingOperations--
            if (requestGeneration != generation || id != sessionId) return@input
            showJournal(view)
            taskFeedback = view.message
            timedGate = timedGate.stop(SystemClock.elapsedRealtime())
            if (view.stored == null || view.readOnly || session.state != SessionState.SAFE_STOP) {
                taskWriteBlocked = true
            }
            if (hideAfter) showTimedTask = false
        }
    }

    override fun onPause() {
        activityInForeground = false
        if (showTimedTask && session.state == SessionState.BASELINE_TASK &&
            timedGate.stage == TimedGateStage.TASK && pendingOperations == 0
        ) {
            pauseTimedTask()
        }
        if (showSession) {
            val edges = SessionGraph.transitions.getValue(session.state)
            when {
                "technical_interrupt" in edges ->
                    applySessionInput(null, SessionControls(technicalInterrupt = true))
                "pause" in edges -> applySessionInput(null, SessionControls(childPause = true))
            }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activityInForeground = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (allowed()) initializeJournal()
        setContent {
            ArcAdhdApp(
                agreement = agreement,
                informationPage = informationPage,
                onInstructions = {
                    instructionsReturn = informationPage
                    informationPage = "instructions"
                },
                onCloseInstructions = { informationPage = instructionsReturn },
                onAgreement = { informationPage = "agreement" },
                onAccept = ::acceptAgreement,
                onDecline = { informationPage = null },
                onWithdraw = ::withdrawAgreement,
                variant = StudyVariant.current(),
                articleModule = CurrentArticleModule,
                timedGate = timedGate,
                showTimedTask = showTimedTask,
                taskFeedback = taskFeedback,
                taskWriteBlocked = taskWriteBlocked,
                selectedStrategy = selectedStrategy,
                session = session,
                showSession = showSession,
                sessionFeedback = sessionFeedback,
                sessionId = sessionId,
                journalBusy = pendingOperations > 0,
                journalReadOnly = journalReadOnly,
                canCreateSession = journalReady && storageHealthy,
                journalReady = journalReady,
                onSessionInput = { previous, event, controls ->
                    if (session == previous || controls != SessionControls()) {
                        applySessionInput(event, controls)
                    }
                },
                onResetSession = { mode -> createSession(mode) },
                onOpenSession = {
                    if (allowed()) {
                        if (sessionId != null) showSession = true else createSession(SessionMode.CORE)
                    }
                },
                onExitSession = {
                    if (session.state !in SessionGraph.terminal) {
                        applySessionInput(null, SessionControls(childStop = true))
                    }
                    showSession = false
                },
                onOpenTimedTask = ::startTimedCoreDemo,
                onTimedStart = {
                    if (pendingOperations == 0 && !taskWriteBlocked) {
                        timedGate = timedGate.start(SystemClock.elapsedRealtime())
                    }
                },
                onTimedGo = ::handleTimedGo,
                onTimedTimeout = ::handleTimedTimeout,
                onTimedPause = ::pauseTimedTask,
                onTimedResume = ::resumeTimedTask,
                onTimedStop = { stopTimedTask() },
                onChallengeDelivered = {
                    runCoreAction(
                        EngineeringCoreAction.CHALLENGE_DELIVERED,
                        SessionState.REGULATION_PAUSE,
                    )
                },
                onPauseComplete = {
                    runCoreAction(
                        EngineeringCoreAction.PAUSE_COMPLETE,
                        SessionState.ORIENT,
                    )
                },
                onOrientationComplete = {
                    runCoreAction(
                        EngineeringCoreAction.ORIENTATION_COMPLETE,
                        SessionState.STRATEGY_SELECTION,
                    )
                },
                onStrategySelected = ::selectStrategy,
                onStrategyComplete = {
                    runCoreAction(
                        EngineeringCoreAction.STRATEGY_COMPLETE_WITHOUT_REFERENCE,
                        SessionState.RECOVERY_DATA_INVALID,
                    )
                },
                onContinueAfterInvalidRecovery = ::continueAfterInvalidRecovery,
                onReflectionComplete = {
                    runCoreAction(
                        EngineeringCoreAction.REFLECTION_COMPLETE,
                        SessionState.SESSION_COMPLETE,
                    )
                },
                onExitTimedTask = {
                    if (session.state in SessionGraph.terminal) showTimedTask = false
                    else stopTimedTask(hideAfter = true)
                },
            )
        }
    }
}

@Composable
private fun ArcAdhdApp(
    agreement: DemoAgreement?,
    informationPage: String?,
    onInstructions: () -> Unit,
    onCloseInstructions: () -> Unit,
    onAgreement: () -> Unit,
    onAccept: (ConsentChoices) -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
    variant: StudyVariant,
    articleModule: ArticleModule,
    timedGate: TimedSignalGate,
    showTimedTask: Boolean,
    taskFeedback: String,
    taskWriteBlocked: Boolean,
    selectedStrategy: String?,
    session: SessionSnapshot,
    showSession: Boolean,
    sessionFeedback: String,
    sessionId: String?,
    journalBusy: Boolean,
    journalReadOnly: Boolean,
    canCreateSession: Boolean,
    journalReady: Boolean,
    onSessionInput: (SessionSnapshot, String?, SessionControls) -> Unit,
    onResetSession: (SessionMode) -> Unit,
    onOpenSession: () -> Unit,
    onExitSession: () -> Unit,
    onOpenTimedTask: () -> Unit,
    onTimedStart: () -> Unit,
    onTimedGo: (String) -> Unit,
    onTimedTimeout: () -> Unit,
    onTimedPause: () -> Unit,
    onTimedResume: () -> Unit,
    onTimedStop: () -> Unit,
    onChallengeDelivered: () -> Unit,
    onPauseComplete: () -> Unit,
    onOrientationComplete: () -> Unit,
    onStrategySelected: (String) -> Unit,
    onStrategyComplete: () -> Unit,
    onContinueAfterInvalidRecovery: () -> Unit,
    onReflectionComplete: () -> Unit,
    onExitTimedTask: () -> Unit,
) {
    ArcTheme {
        Scaffold { padding ->
            if (informationPage == "instructions") {
                InstructionsScreen(
                    variant.paperId,
                    onCloseInstructions,
                    Modifier.fillMaxSize().padding(padding),
                )
            } else if (informationPage == "agreement") {
                AgreementScreen(
                    variant.paperId,
                    agreement,
                    onAccept,
                    onDecline,
                    onWithdraw,
                    onInstructions,
                    Modifier.fillMaxSize().padding(padding),
                )
            } else if (showTimedTask && agreement != null) {
                TimedCoreSignalGateScreen(
                    gate = timedGate,
                    sessionState = session.state,
                    busy = journalBusy,
                    locked = taskWriteBlocked || journalReadOnly,
                    feedback = taskFeedback,
                    selectedStrategy = selectedStrategy,
                    onStart = onTimedStart,
                    onGo = onTimedGo,
                    onTimeout = onTimedTimeout,
                    onPause = onTimedPause,
                    onResume = onTimedResume,
                    onStop = onTimedStop,
                    onChallengeDelivered = onChallengeDelivered,
                    onPauseComplete = onPauseComplete,
                    onOrientationComplete = onOrientationComplete,
                    onStrategySelected = onStrategySelected,
                    onStrategyComplete = onStrategyComplete,
                    onContinueAfterInvalidRecovery = onContinueAfterInvalidRecovery,
                    onReflectionComplete = onReflectionComplete,
                    onExit = onExitTimedTask,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else if (showSession && agreement != null) {
                SessionPreviewScreen(
                    snapshot = session,
                    feedback = sessionFeedback,
                    sessionId = sessionId,
                    busy = journalBusy,
                    readOnly = journalReadOnly,
                    canCreate = canCreateSession,
                    onInput = { event, controls -> onSessionInput(session, event, controls) },
                    onReset = onResetSession,
                    onExit = onExitSession,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                HomeScreen(
                    variant = variant,
                    module = articleModule,
                    consentAccepted = agreement != null,
                    onInstructions = onInstructions,
                    onAgreement = onAgreement,
                    onWithdraw = onWithdraw,
                    ready = journalReady,
                    busy = journalBusy,
                    hasSession = sessionId != null,
                    canCreate = canCreateSession,
                    readOnly = journalReadOnly,
                    feedback = sessionFeedback,
                    onSession = onOpenSession,
                    onSignalGate = onOpenTimedTask,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }
}
