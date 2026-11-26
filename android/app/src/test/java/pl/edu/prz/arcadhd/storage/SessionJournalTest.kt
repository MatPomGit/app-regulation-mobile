package pl.edu.prz.arcadhd.storage

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import pl.edu.prz.arcadhd.session.*

class SessionJournalTest {
    private val context = RecoveryContext("A", 1, 0, 1800, "synthetic-test", "none")

    private fun command(snapshot: SessionSnapshot, event: String) = SessionCommand(
        event, snapshot.lastTimestampMs + 1, context = context, expectedSequence = snapshot.sequence,
    )

    private fun <T> database(block: (String, JdbcJournalSql, SessionJournal) -> T): T {
        val file = Files.createTempFile("arc-engineering-test", ".db")
        try {
            JdbcJournalSql(file.toString()).use { sql ->
                SessionJournalSchema.create(sql)
                return block(file.toString(), sql, SessionJournal(sql))
            }
        } finally { Files.deleteIfExists(file) }
    }

    private fun fail(block: () -> Unit): Exception {
        try { block() } catch (error: Exception) { return error }
        throw AssertionError("Expected failure")
    }

    @Test fun commitSurvivesConnectionCloseAndReplaysTheExactCheckpoint() = database { path, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        var snapshot = created.snapshot
        listOf(
            "initialized", "configuration_loaded", "qualified_core", "onboarding_complete",
            "ready_without_tutorial", "calibration_complete", "challenge_candidate", "eligible",
            "challenge_delivered", "pause_complete", "orientation_complete", "strategy_selected",
            "strategy_complete", "criterion_stable", "event_persisted", "continue_task",
        ).forEach { snapshot = store.append(created.sessionId, command(snapshot, it)).snapshot }
        assertEquals(SessionState.BASELINE_TASK, snapshot.state)
        sql.close()
        JdbcJournalSql(path).use { reopened ->
            val restored = SessionJournal(reopened).active()!!
            assertEquals(created.sessionId, restored.sessionId)
            assertEquals(snapshot, restored.snapshot)
            assertEquals(16, reopened.query("SELECT * FROM journal_queue").size)
        }
    }

    @Test fun identicalRetryPreservesIdAndDelayedRetryDoesNotRegressState() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val first = command(created.snapshot, "initialized")
        val next = store.append(created.sessionId, first).snapshot
        assertEquals(next, store.append(created.sessionId, first).snapshot)
        val latest = store.append(created.sessionId, command(next, "configuration_loaded")).snapshot
        assertEquals(latest, store.append(created.sessionId, first).snapshot)
        assertEquals(2, sql.query("SELECT * FROM journal_events").size)
        assertEquals("${created.sessionId}:1", sql.query(
            "SELECT event_id FROM journal_events WHERE sequence_number=1",
        ).single()["event_id"])
        assertEquals("event_identity_conflict", (fail {
            store.append(created.sessionId, first.copy(timestampMs = 999))
        } as JournalIntegrityException).code)
    }

    @Test fun checkpointFailureRollsBackEventQueueAndSessionTogether() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val input = command(created.snapshot, "initialized")
        sql.failOnSql = "UPDATE journal_sessions SET current_snapshot"
        fail { store.append(created.sessionId, input) }
        sql.failOnSql = null
        assertEquals(created.snapshot, store.load(created.sessionId).snapshot)
        assertTrue(sql.query("SELECT * FROM journal_events").isEmpty())
        assertTrue(sql.query("SELECT * FROM journal_queue").isEmpty())
        assertEquals(1L, store.append(created.sessionId, input).snapshot.sequence)
    }

    @Test fun queueFailureAlsoRollsBackTheEventAndCheckpoint() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        sql.execute("DROP TRIGGER journal_enqueue")
        sql.execute("CREATE TRIGGER fail_enqueue AFTER INSERT ON journal_events " +
            "BEGIN SELECT RAISE(ABORT,'injected_queue_failure'); END")
        fail { store.append(created.sessionId, command(created.snapshot, "initialized")) }
        assertEquals(created.snapshot, store.load(created.sessionId).snapshot)
        assertTrue(sql.query("SELECT * FROM journal_events").isEmpty())
    }

    @Test fun partialAckAndCrashRecoveryPreserveAllOriginalEventIds() = database { path, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val first = store.append(created.sessionId, command(created.snapshot, "initialized")).snapshot
        store.append(created.sessionId, command(first, "configuration_loaded"))
        val batch = store.claim(2)
        val ids = batch.map { it.getValue("event_id")!! }
        store.acknowledge(listOf(ids.first()), "synthetic-receipt-1")
        store.acknowledge(listOf(ids.first()), "synthetic-receipt-1")
        sql.close()
        JdbcJournalSql(path).use { reopened ->
            val restored = SessionJournal(reopened)
            restored.recoverInFlight()
            val retry = restored.claim(10)
            assertEquals(listOf(ids.last()), retry.map { it["event_id"] })
            assertEquals(batch.last()["command_payload"], retry.single()["command_payload"])
            val queue = reopened.query("SELECT * FROM journal_queue ORDER BY event_id")
            assertEquals("acknowledged", queue.first()["delivery_state"])
            assertEquals("2", queue.last()["attempt_count"])
            restored.returnToPending(listOf(ids.last()), "timeout")
            assertEquals(ids.last(), restored.claim(1).single()["event_id"])
        }
    }

    @Test fun malformedAckIsAtomicAndPendingEventsCannotBeAcknowledged() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        store.append(created.sessionId, command(created.snapshot, "initialized"))
        val id = "${created.sessionId}:1"
        fail { store.acknowledge(listOf(id), "receipt") }
        store.claim(1)
        fail { store.acknowledge(listOf(id, "unknown"), "receipt") }
        assertEquals("in_flight", sql.query("SELECT * FROM journal_queue").single()["delivery_state"])
    }

    @Test fun corruptionMissingRowsAndChangedGraphFailClosedWithoutReset() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        store.append(created.sessionId, command(created.snapshot, "initialized"))
        sql.execute("UPDATE journal_sessions SET snapshot_hash='corrupted'")
        assertEquals("checkpoint_mismatch", (fail { store.load(created.sessionId) }
            as JournalIntegrityException).code)
        assertEquals(1, sql.query("SELECT * FROM journal_events").size)
        sql.execute("UPDATE journal_sessions SET graph_hash='different'")
        assertEquals("configuration_mismatch", (fail { store.load(created.sessionId) }
            as JournalIntegrityException).code)
    }

    @Test fun replayRejectsTamperedInputEvenWhenPayloadChecksumIsRecomputed() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        store.append(created.sessionId, command(created.snapshot, "initialized"))
        val row = sql.query("SELECT * FROM journal_events").single()
        val altered = SessionJournalCodec.command(command(created.snapshot, "configuration_loaded"))
        sql.execute("DROP TRIGGER journal_event_immutable")
        sql.execute("UPDATE journal_events SET command_payload=?,payload_hash=?", listOf(
            altered, SessionJournalCodec.hash(
                row.getValue("event_id")!!, altered, row.getValue("snapshot_payload")!!,
            ),
        ))
        assertEquals("replay_mismatch", (fail { store.load(created.sessionId) }
            as JournalIntegrityException).code)
    }

    @Test fun immutableEventsMissingQueueAndSequenceGapsAreDetected() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val first = store.append(created.sessionId, command(created.snapshot, "initialized")).snapshot
        store.append(created.sessionId, command(first, "configuration_loaded"))
        fail { sql.execute("UPDATE journal_events SET command_payload='rewrite'") }
        sql.execute("DELETE FROM journal_queue WHERE event_id=?", listOf("${created.sessionId}:1"))
        assertEquals("queue_record_missing", (fail { store.load(created.sessionId) }
            as JournalIntegrityException).code)
        sql.execute("DELETE FROM journal_events WHERE event_id=?", listOf("${created.sessionId}:1"))
        assertEquals("event_integrity_failure", (fail { store.load(created.sessionId) }
            as JournalIntegrityException).code)
    }

    @Test fun rejectedTransitionWritesNothingAndNewSessionRetainsPriorJournal() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val decision = store.append(created.sessionId, command(created.snapshot, "criterion_stable"))
        assertEquals("event_not_allowed", decision.rejection)
        assertTrue(sql.query("SELECT * FROM journal_events").isEmpty())
        val next = store.create(SessionMode.ACTIVE_CONTROL, "boot-A", "process-A")
        assertEquals(next.sessionId, store.active()!!.sessionId)
        assertEquals(created, store.load(created.sessionId))
        assertEquals(2, sql.query("SELECT * FROM journal_sessions").size)
    }
    private fun trial(
        runId: String = "run-A",
        index: Int,
        trialId: String = "$runId-t${index.toString().padStart(3, '0')}",
    ) = EngineeringTrialRecord(
        taskRunId = runId,
        trialId = trialId,
        trialIndex = index,
        ruleId = if (index < 4) "A" else "B",
        stimulusId = if (index % 2 == 0) "BLUE" else "ORANGE",
        responseKind = if (index % 2 == 0) "GO_TAP" else "WITHHOLD_TIMEOUT",
        expectedGo = index % 2 == 0,
        correct = true,
        shownAtMs = 1_000L + index * 2_000L,
        respondedAtMs = 1_500L + index * 2_000L,
    )

    @Test fun engineeringTrialsSurviveReopenAndNeverEnterDeliveryQueue() = database { path, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        assertEquals(trial(index = 0), store.appendTrial(created.sessionId, trial(index = 0)))
        assertEquals(trial(index = 1), store.appendTrial(created.sessionId, trial(index = 1)))
        assertTrue(sql.query("SELECT * FROM journal_queue").isEmpty())
        assertEquals(2, store.trials(created.sessionId, "run-A").size)

        sql.close()
        JdbcJournalSql(path).use { reopened ->
            val restored = SessionJournal(reopened)
            assertEquals(listOf(trial(index = 0), trial(index = 1)),
                restored.trials(created.sessionId, "run-A"))
            assertTrue(reopened.query("SELECT * FROM journal_queue").isEmpty())
        }
    }

    @Test fun engineeringTrialRetriesAreIdempotentAndConflictsFailClosed() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        val first = trial(index = 0)
        assertEquals(first, store.appendTrial(created.sessionId, first))
        assertEquals(first, store.appendTrial(created.sessionId, first))
        assertEquals(1, sql.query("SELECT * FROM engineering_trials").size)

        val conflict = first.copy(correct = false)
        assertEquals("trial_identity_conflict", (fail {
            store.appendTrial(created.sessionId, conflict)
        } as JournalIntegrityException).code)
    }

    @Test fun engineeringTrialsRejectGapsAndTampering() = database { _, sql, store ->
        val created = store.create(SessionMode.CORE, "boot-A", "process-A")
        assertEquals("trial_sequence_gap", (fail {
            store.appendTrial(created.sessionId, trial(index = 1))
        } as JournalIntegrityException).code)

        store.appendTrial(created.sessionId, trial(index = 0))
        fail { sql.execute("UPDATE engineering_trials SET correct=0") }
        sql.execute("DROP TRIGGER engineering_trial_immutable")
        sql.execute("UPDATE engineering_trials SET payload_hash='tampered'")
        assertEquals("trial_integrity_failure", (fail {
            store.load(created.sessionId)
        } as JournalIntegrityException).code)
    }

    @Test fun alteredTrialIdentityFailsClosedEvenWithIntactPayloadHash() =
        database { _, sql, store ->
            val created = store.create(SessionMode.CORE, "boot-A", "process-A")
            store.appendTrial(created.sessionId, trial(index = 0))
            val original = sql.query("SELECT * FROM engineering_trials").single()
            assertEquals(
                "${created.sessionId}:trial:run-A:0",
                original["event_id"],
            )

            // The immutable-table trigger protects normal writes. Simulate external file corruption.
            sql.execute("DROP TRIGGER engineering_trial_immutable")
            sql.execute(
                "UPDATE engineering_trials SET event_id=? WHERE event_id=?",
                listOf("${created.sessionId}:trial:forged:0", original.getValue("event_id")),
            )
            assertEquals(
                original["payload_hash"],
                sql.query("SELECT * FROM engineering_trials").single()["payload_hash"],
            )
            assertEquals("trial_integrity_failure", (fail {
                store.load(created.sessionId)
            } as JournalIntegrityException).code)
            assertEquals("trial_integrity_failure", (fail {
                store.trials(created.sessionId, "run-A")
            } as JournalIntegrityException).code)
        }

    @Test fun versionOneJournalMigratesWithoutLosingExistingSession() {
        val file = Files.createTempFile("arc-engineering-migration", ".db")
        try {
            JdbcJournalSql(file.toString()).use { sql ->
                SessionJournalSchema.version1Statements.forEach { sql.execute(it) }
                val store = SessionJournal(sql)
                val created = store.create(SessionMode.CORE, "boot-A", "process-A")
                assertTrue(SessionJournalSchema.upgrade(sql, 1, 2))
                val migrated = SessionJournal(sql)
                assertEquals(created, migrated.load(created.sessionId))
                assertEquals(trial(index = 0),
                    migrated.appendTrial(created.sessionId, trial(index = 0)))
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

}
