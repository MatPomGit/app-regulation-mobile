package pl.edu.prz.arcadhd.storage

import java.util.UUID
import pl.edu.prz.arcadhd.session.SessionDecision
import pl.edu.prz.arcadhd.session.SessionGraph
import pl.edu.prz.arcadhd.session.SessionMode
import pl.edu.prz.arcadhd.session.SessionSnapshot

class JournalIntegrityException(val code: String) : IllegalStateException(code)

data class StoredSession(
    val sessionId: String,
    val snapshot: SessionSnapshot,
    val clockDomain: String,
    val runtimeToken: String,
)

/** Transactional engineering journal. All methods must use a serialized database connection. */
class SessionJournal(private val sql: JournalSql) {
    fun create(mode: SessionMode, clockDomain: String, runtimeToken: String): StoredSession =
        sql.transaction {
            require(clockDomain.isNotBlank() && runtimeToken.isNotBlank())
            val id = UUID.randomUUID().toString()
            val initial = SessionSnapshot(mode = mode)
            val payload = SessionJournalCodec.snapshot(initial)
            sql.execute(
                "INSERT INTO journal_sessions VALUES(?,?,?,?,?,?,?,0)",
                listOf(id, SessionGraph.SOURCE_SHA256, clockDomain, runtimeToken,
                    payload, payload, SessionJournalCodec.hash(payload)),
            )
            sql.execute("DELETE FROM journal_active")
            sql.execute("INSERT INTO journal_active VALUES(1,?)", listOf(id))
            StoredSession(id, initial, clockDomain, runtimeToken)
        }

    fun active(): StoredSession? = sql.transaction {
        sql.query("SELECT session_id FROM journal_active")
            .singleOrNull()?.getValue("session_id")?.let { readVerified(it) }
    }

    /** Verify hashes, contiguous sequence and deterministic replay before exposing a checkpoint. */
    fun load(sessionId: String): StoredSession = sql.transaction { readVerified(sessionId) }

    private fun readVerified(sessionId: String): StoredSession {
        val header = sql.query(
            "SELECT * FROM journal_sessions WHERE session_id=?", listOf(sessionId),
        ).singleOrNull() ?: throw JournalIntegrityException("session_not_found")
        if (header["graph_hash"] != SessionGraph.SOURCE_SHA256) {
            throw JournalIntegrityException("configuration_mismatch")
        }
        val initialPayload = header.getValue("initial_snapshot")!!
        var snapshot = SessionJournalCodec.snapshot(initialPayload)
        if (snapshot.sequence != 0L || snapshot != SessionSnapshot(mode = snapshot.mode)) {
            throw JournalIntegrityException("invalid_initial_snapshot")
        }
        val rows = sql.query(
            "SELECT * FROM journal_events WHERE session_id=? ORDER BY sequence_number",
            listOf(sessionId),
        )
        rows.forEach { row ->
            val sequence = row.getValue("sequence_number")!!.toLong()
            val eventId = "$sessionId:$sequence"
            val commandPayload = row.getValue("command_payload")!!
            val snapshotPayload = row.getValue("snapshot_payload")!!
            if (sequence != snapshot.sequence + 1 || row["event_id"] != eventId ||
                row["payload_hash"] != SessionJournalCodec.hash(eventId, commandPayload, snapshotPayload)
            ) throw JournalIntegrityException("event_integrity_failure")
            val command = SessionJournalCodec.command(commandPayload)
            if (command.expectedSequence != snapshot.sequence) {
                throw JournalIntegrityException("command_sequence_conflict")
            }
            val decision = command.apply(snapshot)
            if (decision.rejection != null || decision.transition == null ||
                SessionJournalCodec.snapshot(decision.snapshot) != snapshotPayload
            ) throw JournalIntegrityException("replay_mismatch")
            snapshot = decision.snapshot
        }
        val current = SessionJournalCodec.snapshot(snapshot)
        if (current != header["current_snapshot"] ||
            header["snapshot_hash"] != SessionJournalCodec.hash(current) ||
            snapshot.sequence != header.getValue("last_sequence")!!.toLong()
        ) throw JournalIntegrityException("checkpoint_mismatch")
        val queueCount = sql.query(
            "SELECT COUNT(*) AS count FROM journal_queue q JOIN journal_events e " +
                "ON e.event_id=q.event_id WHERE e.session_id=?", listOf(sessionId),
        ).single().getValue("count")!!.toInt()
        if (queueCount != rows.size) throw JournalIntegrityException("queue_record_missing")
        verifyTrials(sessionId)
        return StoredSession(
            sessionId, snapshot, header.getValue("clock_domain")!!, header.getValue("runtime_token")!!,
        )
    }

    /** Atomic event + pending queue + checkpoint commit; an identical retry never creates another ID. */
    fun append(sessionId: String, command: SessionCommand): SessionDecision = sql.transaction {
        val stored = readVerified(sessionId)
        val payload = SessionJournalCodec.command(command)
        val sequence = command.expectedSequence + 1
        val eventId = "$sessionId:$sequence"
        val duplicate = sql.query(
            "SELECT command_payload FROM journal_events WHERE event_id=?", listOf(eventId),
        ).singleOrNull()
        if (duplicate != null) {
            if (duplicate["command_payload"] != payload) {
                throw JournalIntegrityException("event_identity_conflict")
            }
            return@transaction SessionDecision(stored.snapshot)
        }
        if (command.expectedSequence != stored.snapshot.sequence) {
            throw JournalIntegrityException("stale_checkpoint")
        }
        val decision = command.apply(stored.snapshot)
        if (decision.rejection != null) return@transaction decision
        check(decision.transition != null)
        val next = SessionJournalCodec.snapshot(decision.snapshot)
        sql.execute(
            "INSERT INTO journal_events VALUES(?,?,?,?,?,?)",
            listOf(eventId, sessionId, sequence, payload, next,
                SessionJournalCodec.hash(eventId, payload, next)),
        )
        sql.execute(
            "UPDATE journal_sessions SET current_snapshot=?,snapshot_hash=?,last_sequence=? " +
                "WHERE session_id=?",
            listOf(next, SessionJournalCodec.hash(next), sequence, sessionId),
        )
        decision
    }

    /**
     * Persist one completed engineering trial without creating an export queue record.
     *
     * Identical retries are idempotent. A conflicting retry with the same event identity fails closed.
     */
    fun appendTrial(sessionId: String, record: EngineeringTrialRecord): EngineeringTrialRecord =
        sql.transaction {
            readVerified(sessionId)
            val eventId = "$sessionId:trial:${record.taskRunId}:${record.trialIndex}"
            val duplicate = sql.query(
                "SELECT * FROM engineering_trials WHERE event_id=?", listOf(eventId),
            ).singleOrNull()
            if (duplicate != null) {
                val existing = trialFromRow(duplicate)
                if (existing != record) throw JournalIntegrityException("trial_identity_conflict")
                return@transaction existing
            }

            val prior = sql.query(
                "SELECT trial_index FROM engineering_trials " +
                    "WHERE session_id=? AND task_run_id=? ORDER BY trial_index",
                listOf(sessionId, record.taskRunId),
            )
            if (prior.size != record.trialIndex ||
                prior.indices.any { index -> prior[index]["trial_index"]?.toInt() != index }
            ) {
                throw JournalIntegrityException("trial_sequence_gap")
            }

            sql.execute(
                "INSERT INTO engineering_trials VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                listOf(
                    eventId,
                    sessionId,
                    record.taskRunId,
                    record.trialId,
                    record.trialIndex,
                    record.ruleId,
                    record.stimulusId,
                    record.responseKind,
                    if (record.expectedGo) 1 else 0,
                    if (record.correct) 1 else 0,
                    record.shownAtMs,
                    record.respondedAtMs,
                    record.latencyMs,
                    record.payloadHash(),
                ),
            )
            record
        }

    /** Read and verify immutable engineering trials for one task run. */
    fun trials(sessionId: String, taskRunId: String): List<EngineeringTrialRecord> =
        sql.transaction {
            require(taskRunId.isNotBlank())
            readVerified(sessionId)
            sql.query(
                "SELECT * FROM engineering_trials WHERE session_id=? AND task_run_id=? " +
                    "ORDER BY trial_index",
                listOf(sessionId, taskRunId),
            ).map(::trialFromRow).also { records ->
                records.forEachIndexed { index, record ->
                    if (record.trialIndex != index) throw JournalIntegrityException("trial_sequence_gap")
                }
            }
        }

    private fun verifyTrials(sessionId: String) {
        val rows = sql.query(
            "SELECT * FROM engineering_trials WHERE session_id=? ORDER BY task_run_id,trial_index",
            listOf(sessionId),
        )
        var currentRun: String? = null
        var expectedIndex = 0
        rows.forEach { row ->
            val record = trialFromRow(row)
            if (record.taskRunId != currentRun) {
                currentRun = record.taskRunId
                expectedIndex = 0
            }
            if (record.trialIndex != expectedIndex) {
                throw JournalIntegrityException("trial_sequence_gap")
            }
            expectedIndex++
        }
    }

    private fun trialFromRow(row: Map<String, String?>): EngineeringTrialRecord {
        val record = EngineeringTrialRecord(
            taskRunId = row.getValue("task_run_id")!!,
            trialId = row.getValue("trial_id")!!,
            trialIndex = row.getValue("trial_index")!!.toInt(),
            ruleId = row.getValue("rule_id")!!,
            stimulusId = row.getValue("stimulus_id")!!,
            responseKind = row.getValue("response_kind")!!,
            expectedGo = parseSqlBoolean(row.getValue("expected_go")),
            correct = parseSqlBoolean(row.getValue("correct")),
            shownAtMs = row.getValue("shown_at_ms")!!.toLong(),
            respondedAtMs = row.getValue("responded_at_ms")!!.toLong(),
        )
        val expectedId = "${row.getValue("session_id")}:trial:${record.taskRunId}:${record.trialIndex}"
        if (row["event_id"] != expectedId ||
            row["latency_ms"]?.toLong() != record.latencyMs ||
            row["payload_hash"] != record.payloadHash()
        ) {
            throw JournalIntegrityException("trial_integrity_failure")
        }
        return record
    }

    private fun parseSqlBoolean(value: String?): Boolean = when (value) {
        "0" -> false
        "1" -> true
        else -> throw JournalIntegrityException("trial_integrity_failure")
    }

    fun bindRuntime(sessionId: String, token: String) {
        require(token.isNotBlank())
        sql.execute("UPDATE journal_sessions SET runtime_token=? WHERE session_id=?", listOf(token, sessionId))
    }

    /** Expose bounded immutable records for a future transport; the application has no uploader. */
    fun claim(limit: Int): List<Map<String, String?>> = sql.transaction {
        require(limit in 1..100)
        val rows = sql.query(
            "SELECT e.* FROM journal_events e JOIN journal_queue q ON q.event_id=e.event_id " +
                "WHERE q.delivery_state='pending' ORDER BY e.session_id,e.sequence_number LIMIT ?",
            listOf(limit),
        )
        rows.map { it.getValue("session_id")!! }.distinct().forEach { readVerified(it) }
        rows.forEach {
            sql.execute(
                "UPDATE journal_queue SET delivery_state='in_flight',attempt_count=attempt_count+1 " +
                    "WHERE event_id=?", listOf(it.getValue("event_id")),
            )
        }
        rows
    }

    /** Receipt handling is per event ID. No deletion or whole-batch success shortcut exists. */
    fun acknowledge(eventIds: List<String>, receipt: String) = sql.transaction {
        require(receipt.isNotBlank() && eventIds.isNotEmpty())
        eventIds.distinct().forEach { id ->
            val row = sql.query("SELECT * FROM journal_queue WHERE event_id=?", listOf(id))
                .singleOrNull() ?: throw JournalIntegrityException("unknown_acknowledgement")
            if (row["delivery_state"] !in setOf("in_flight", "acknowledged")) {
                throw JournalIntegrityException("acknowledgement_without_attempt")
            }
            if (row["delivery_state"] != "acknowledged") {
                sql.execute(
                    "UPDATE journal_queue SET delivery_state='acknowledged',acknowledgement_id=? " +
                        "WHERE event_id=?", listOf(receipt, id),
                )
            }
        }
    }

    /** Retry only attempted records; acknowledged events and identities remain untouched. */
    fun returnToPending(eventIds: List<String>, errorCode: String) = sql.transaction {
        require(errorCode in setOf("network_unavailable", "timeout", "server_unavailable"))
        eventIds.distinct().forEach { id ->
            sql.execute(
                "UPDATE journal_queue SET delivery_state='pending',error_code=? " +
                    "WHERE event_id=? AND delivery_state='in_flight'", listOf(errorCode, id),
            )
        }
    }

    fun recoverInFlight() {
        sql.execute("UPDATE journal_queue SET delivery_state='pending' WHERE delivery_state='in_flight'")
    }
}
