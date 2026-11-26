package pl.edu.prz.arcadhd.storage

/** Small SQL boundary shared by Android's SQLite adapter and file-backed JVM integration tests. */
interface JournalSql {
    fun execute(sql: String, args: List<Any?> = emptyList())
    fun query(sql: String, args: List<Any?> = emptyList()): List<Map<String, String?>>
    fun <T> transaction(block: () -> T): T
}

/** Separate engineering database; never creates participant identities or scientific event rows. */
object SessionJournalSchema {
    const val VERSION = 2

    internal val version1Statements = listOf(
        """CREATE TABLE journal_sessions (
            session_id TEXT PRIMARY KEY,
            graph_hash TEXT NOT NULL,
            clock_domain TEXT NOT NULL,
            runtime_token TEXT NOT NULL,
            initial_snapshot TEXT NOT NULL,
            current_snapshot TEXT NOT NULL,
            snapshot_hash TEXT NOT NULL,
            last_sequence INTEGER NOT NULL CHECK(last_sequence >= 0)
        )""",
        """CREATE TABLE journal_events (
            event_id TEXT PRIMARY KEY,
            session_id TEXT NOT NULL REFERENCES journal_sessions(session_id),
            sequence_number INTEGER NOT NULL CHECK(sequence_number > 0),
            command_payload TEXT NOT NULL,
            snapshot_payload TEXT NOT NULL,
            payload_hash TEXT NOT NULL,
            UNIQUE(session_id, sequence_number)
        )""",
        """CREATE TABLE journal_queue (
            event_id TEXT PRIMARY KEY REFERENCES journal_events(event_id),
            delivery_state TEXT NOT NULL DEFAULT 'pending'
                CHECK(delivery_state IN ('pending','in_flight','acknowledged','blocked')),
            attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count >= 0),
            acknowledgement_id TEXT,
            error_code TEXT,
            CHECK((delivery_state = 'acknowledged') = (acknowledgement_id IS NOT NULL))
        )""",
        """CREATE TABLE journal_active (
            slot INTEGER PRIMARY KEY CHECK(slot = 1),
            session_id TEXT NOT NULL REFERENCES journal_sessions(session_id)
        )""",
        """CREATE TRIGGER journal_event_immutable BEFORE UPDATE ON journal_events
            BEGIN SELECT RAISE(ABORT, 'journal_event_immutable'); END""",
        """CREATE TRIGGER journal_enqueue AFTER INSERT ON journal_events
            BEGIN INSERT INTO journal_queue(event_id) VALUES(NEW.event_id); END""",
        "CREATE INDEX journal_queue_state ON journal_queue(delivery_state)",
    )

    internal val version2Statements = listOf(
        """CREATE TABLE engineering_trials (
            event_id TEXT PRIMARY KEY,
            session_id TEXT NOT NULL REFERENCES journal_sessions(session_id),
            task_run_id TEXT NOT NULL,
            trial_id TEXT NOT NULL,
            trial_index INTEGER NOT NULL CHECK(trial_index >= 0),
            rule_id TEXT NOT NULL,
            stimulus_id TEXT NOT NULL,
            response_kind TEXT NOT NULL,
            expected_go INTEGER NOT NULL CHECK(expected_go IN (0,1)),
            correct INTEGER NOT NULL CHECK(correct IN (0,1)),
            shown_at_ms INTEGER NOT NULL CHECK(shown_at_ms >= 0),
            responded_at_ms INTEGER NOT NULL CHECK(responded_at_ms >= shown_at_ms),
            latency_ms INTEGER NOT NULL CHECK(latency_ms = responded_at_ms - shown_at_ms),
            payload_hash TEXT NOT NULL,
            UNIQUE(session_id, task_run_id, trial_index),
            UNIQUE(session_id, task_run_id, trial_id)
        )""",
        """CREATE TRIGGER engineering_trial_immutable BEFORE UPDATE ON engineering_trials
            BEGIN SELECT RAISE(ABORT, 'engineering_trial_immutable'); END""",
        "CREATE INDEX engineering_trials_run ON engineering_trials(session_id,task_run_id,trial_index)",
    )

    fun create(sql: JournalSql) {
        version1Statements.forEach { sql.execute(it) }
        version2Statements.forEach { sql.execute(it) }
    }

    fun upgrade(sql: JournalSql, oldVersion: Int, newVersion: Int): Boolean {
        if (oldVersion == 1 && newVersion == 2) {
            version2Statements.forEach { sql.execute(it) }
            return true
        }
        return false
    }
}
