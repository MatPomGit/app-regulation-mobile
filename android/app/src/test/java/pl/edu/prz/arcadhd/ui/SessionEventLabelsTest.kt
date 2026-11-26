package pl.edu.prz.arcadhd.ui

import org.junit.Assert.*
import org.junit.Test
import pl.edu.prz.arcadhd.session.SessionGraph

class SessionEventLabelsTest {
    @Test fun everyGraphEventHasADistinctReadableLabel() {
        val events = SessionGraph.transitions.values.flatMap { it.keys }.toSet()
        assertTrue(sessionEventLabels.keys.containsAll(events))
        val labels = events.map(::sessionEventLabel)
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() && '_' !in it })
    }

    @Test fun unknownCodesNeverLeakIntoUserMessages() {
        val privatePayload = "unknown_internal_payload_123"
        assertFalse(sessionEventLabel(privatePayload).contains(privatePayload))
        assertFalse(journalMessage(privatePayload).contains(privatePayload))
    }
}
