package pl.edu.prz.arcadhd.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalGatePreviewTest {
    @Test fun sequenceMatchesUnityAndRequiresExplicitSwitchAcknowledgement() {
        var preview = SignalGatePreview().start()
        val stimuli = listOf(
            GateColor.BLUE, GateColor.ORANGE, GateColor.ORANGE, GateColor.BLUE,
            GateColor.ORANGE, GateColor.BLUE, GateColor.BLUE, GateColor.ORANGE,
        )
        val answers = listOf(true, false, false, true, true, false, false, true)
        stimuli.indices.forEach { index ->
            if (index == 4) {
                assertEquals(PreviewStage.RULE_SWITCH, preview.stage)
                assertEquals(preview, preview.submit(index, true))
                preview = preview.acknowledgeSwitch()
            }
            assertEquals(stimuli[index], preview.currentStimulus)
            assertEquals(if (index < 4) GateRule.A else GateRule.B, preview.activeRule)
            preview = preview.submit(index, answers[index])
        }
        assertEquals(PreviewStage.COMPLETE, preview.stage)
        assertEquals(8, preview.correctCount)
        assertNull(preview.currentStimulus)
        assertEquals(preview, preview.submit(8, true))
    }

    @Test fun wrongAnswersDoNotPreventCompletion() {
        var preview = SignalGatePreview().start().submit(0, false)
        assertEquals(0, preview.correctCount)
        for (index in 1 until 8) {
            if (index == 4) preview = preview.acknowledgeSwitch()
            val go = if (preview.activeRule == GateRule.A) {
                preview.currentStimulus == GateColor.BLUE
            } else preview.currentStimulus == GateColor.ORANGE
            preview = preview.submit(index, go)
        }
        assertEquals(7, preview.correctCount)
        assertEquals(PreviewStage.COMPLETE, preview.stage)
    }

    @Test fun staleAndDuplicateResponsesAreIgnored() {
        val initial = SignalGatePreview().start()
        val advanced = initial.submit(0, true)
        assertEquals(advanced, advanced.submit(0, true))
        assertEquals(1, advanced.responses.size)
        assertEquals(initial, initial.submit(1, true))
        assertEquals(SignalGatePreview(), SignalGatePreview().submit(0, true))
    }

    @Test fun pauseAndBackgroundRequireExplicitResumeWithoutAnAnswer() {
        val task = SignalGatePreview().start().submit(0, true)
        val paused = task.pause()
        assertEquals(PreviewStage.PAUSED, paused.stage)
        assertEquals(paused, paused.pause())
        assertEquals(paused, paused.submit(1, false))
        assertEquals(task, paused.resume())
        assertEquals(task.responses, paused.responses)
    }

    @Test fun pauseOnRuleCueReturnsToCueRatherThanTask() {
        var preview = SignalGatePreview().start()
        repeat(4) { index -> preview = preview.submit(index, true) }
        val paused = preview.pause()
        assertEquals(PreviewStage.RULE_SWITCH, paused.resume().stage)
        assertEquals(paused, paused.acknowledgeSwitch())
    }

    @Test fun stopIsTerminalFromEveryNonterminalStage() {
        val ready = SignalGatePreview()
        val task = ready.start()
        var cue = task
        repeat(4) { index -> cue = cue.submit(index, true) }
        listOf(ready, task, cue, task.pause()).forEach { state ->
            val stopped = state.stop()
            assertEquals(PreviewStage.STOPPED, stopped.stage)
            assertNull(stopped.resumeStage)
            assertEquals(stopped, stopped.start())
            assertEquals(stopped, stopped.resume())
            assertEquals(stopped, stopped.submit(state.trialIndex, true))
            assertEquals(stopped, stopped.acknowledgeSwitch())
            assertEquals(stopped, stopped.pause())
        }
    }
}
