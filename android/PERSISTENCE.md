# Durable engineering journal

Status: implemented for the developer session explorer and timed Signal Gate
engineering flow. Participant readiness remains false. No participant records or
network upload are enabled. Completed timed trials are now persisted locally as
engineering-only records.

## Storage and ownership

AndroidJournalDatabase uses native SQLiteOpenHelper and the private file
engineering-session-journal.db, schema version 2. Foreign keys and synchronous
FULL are enabled. Version 1 upgrades in place by adding the engineering trial
table and preserving existing session journals. Unsupported upgrades fail without
destructive fallback.
Android backup is disabled by the application manifest. The database is not
encrypted and must not contain participant data. Storage protection, retention,
withdrawal and participant export requirements remain design gates.

| Table | Purpose |
| --- | --- |
| journal_sessions | Graph hash, clock domain, process token, initial/current checkpoint and sequence |
| journal_events | Immutable accepted inputs and resulting snapshots, stable IDs and content hashes |
| journal_queue | One delivery record per event, attempt count and per-event acknowledgement |
| journal_active | Pointer to the current demonstration; previous journals are retained |
| engineering_trials | Immutable completed timed Signal Gate trials; no participant identity and no delivery-queue row |

The versioned binary codec uses Base64 with a bounded encoded size, rejects
unknown versions and trailing bytes, and hashes length-prefixed content using
SHA-256. Encoding is not encryption and hashes are not authentication.

## Commit and replay

One transaction inserts an accepted event, enqueues it through a trigger and
updates the session checkpoint. The UI receives the new state only after commit.
Rejected reducer inputs do not write records. A failed insert or checkpoint
update rolls back the whole transaction. IDs combine a random session UUID and
the transition sequence. An identical retry returns the latest checkpoint;
different content under an existing ID is rejected.

Loading checks the graph hash, initial checkpoint, contiguous sequence, event
identities (including session/run/index-derived engineering-trial IDs), hashes,
exact reducer replay, final checkpoint and queue membership.
Mismatch blocks changes and retains the database for inspection. There is no
silent reset, event deletion, or corruption repair API. These checks detect
accidental inconsistency; they do not establish resistance to a malicious editor.

EngineeringJournalRuntime serializes all application database access on one
process-wide IO executor. Callbacks publish state on the Android main thread.
Ordinary buttons are disabled during pending writes. Pause/stop requests can be
queued during a write and are evaluated against the latest committed state.
Persistence failures expose a bounded error code and block further changes.

## Restart policy

| Condition | Behavior |
| --- | --- |
| Activity recreation within the same process | Restore verified state; normal background interruption applies |
| New process within the same boot, ordinary session | Persist technical interruption or pause where supported; continuation is explicit |
| New process with a retained recovery context | Protocol abort; never invent observed recovery or censoring |
| Terminal state | Retain and inspect the terminal checkpoint |
| Changed boot domain or backward monotonic time | Previous session becomes read-only; allow a new demonstration |
| Graph mismatch or invalid journal | Block changes, retain records, show bounded failure reason |

Clock domains use an app-generated random token. The local Android boot count
only decides whether that token can be reused; it is never exported as a
research identifier. An unavailable boot count conservatively creates a new
domain for a new process. The implementation never fabricates a clock offset
across boots. This is an engineering restart policy, not a frozen study rule.

## Queue boundary

Claim selects at most 100 pending immutable events, verifies their sessions,
marks them in flight and increments attempts atomically. Acknowledgements name
specific attempted event IDs; partial acknowledgement is supported. Unknown IDs
or IDs without an attempt reject the whole receipt transaction. Repeated
acknowledgement does not duplicate records or replace the original receipt.
Retries preserve IDs and payloads. Initialization returns orphaned in-flight
records to pending. Acknowledged records are retained; no cleanup is implemented.

No transport invokes these APIs in the application. Engineering session inputs
and `engineering_trials` are not participant envelopes and cannot establish
research export eligibility. The engineering trial table has no enqueue trigger
and is intentionally absent from `journal_queue`. An
uploader still requires frozen allowlists, typed participant mappings, recovery
validators, authorization, server idempotency, retry/backoff and retention rules.
The separately maintained proposed participant schema
is a separate design contract and has not been substituted by this database.

## Verification

SessionJournalTest uses actual temporary SQLite files through a test-only JDBC
adapter (org.xerial:sqlite-jdbc:3.54.1.0). Production uses Android SQLite and does
not package JDBC. Tests cover file reopen, exact replay, rollback after checkpoint
and trigger failures, identical/conflicting retries, partial receipts, rejected
receipts, queue recovery, immutable events, missing records, sequence gaps,
checkpoint/configuration corruption and tampered input replay.
RestartRecoveryTest covers process/clock-domain boundaries, interrupted recovery,
terminal states and strict codec decoding. Trial persistence tests cover v1->v2
migration, reopen durability, idempotent retries, sequence gaps, immutable rows
and tamper detection.

From android/:

    gradle :app:testCoreDebugUnitTest :app:assembleCoreDebug

CI runs shared JVM tests and debug builds for all five flavors. JVM SQL tests
do not execute SQLiteOpenHelper or qualify Android filesystem durability.

Physical-device acceptance checks remain unverified:

1. Advance baseline, rotate, background and force-stop; inspect restoration and
   explicit resume without duplicate event IDs.
2. Force-stop with an open recovery context; confirm protocol abort and no fake
   observed/censored outcome.
3. Reboot; inspect the old journal read-only and create a new demonstration.
4. Exercise low-storage/write failures and rotation during a pending write;
   confirm UI state never advances beyond durable commit.
5. Inspect pinned pause/stop controls on small screens and large font scaling.

Estimated 2–4 further coherent core work packages before a first Phase 0 study
build, subject to configuration freeze and physical-device verification. Optional
measurement modules require additional implementation and qualification.
