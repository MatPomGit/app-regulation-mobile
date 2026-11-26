package pl.edu.prz.arcadhd.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64
import pl.edu.prz.arcadhd.session.*

/** Exact reducer inputs are persisted so replay does not reconstruct decisions from UI history. */
data class SessionCommand(
    val event: String?,
    val timestampMs: Long,
    val controls: SessionControls = SessionControls(),
    val context: RecoveryContext? = null,
    val expectedSequence: Long,
) {
    fun apply(snapshot: SessionSnapshot): SessionDecision = SessionController.reduce(
        snapshot, event, timestampMs, controls, context, expectedSequence,
    )
}

/** Versioned local engineering format. Not a participant research-event envelope. */
object SessionJournalCodec {
    private const val VERSION = 1
    private const val MAX_ENCODED_LENGTH = 16384

    fun snapshot(value: SessionSnapshot): String = encode {
        writeUTF(value.state.name)
        writeUTF(value.mode.name)
        writeLong(value.sequence)
        writeLong(value.lastTimestampMs)
        nullableString(value.resumeTarget?.name)
        context(value.recoveryContext)
    }

    fun snapshot(encoded: String): SessionSnapshot = decode(encoded) {
        SessionSnapshot(
            state = SessionState.valueOf(readUTF()),
            mode = SessionMode.valueOf(readUTF()),
            sequence = readLong(),
            lastTimestampMs = readLong(),
            resumeTarget = nullableString()?.let { SessionState.valueOf(it) },
            recoveryContext = context(),
        )
    }

    fun command(value: SessionCommand): String = encode {
        nullableString(value.event)
        writeLong(value.timestampMs)
        with(value.controls) {
            writeBoolean(fatalIntegrity); writeBoolean(safetyStop); writeBoolean(childStop)
            writeBoolean(childPause); writeBoolean(protocolAbort); writeBoolean(technicalInterrupt)
        }
        context(value.context)
        writeLong(value.expectedSequence)
    }

    fun command(encoded: String): SessionCommand = decode(encoded) {
        SessionCommand(
            event = nullableString(),
            timestampMs = readLong(),
            controls = SessionControls(
                readBoolean(), readBoolean(), readBoolean(),
                readBoolean(), readBoolean(), readBoolean(),
            ),
            context = context(),
            expectedSequence = readLong(),
        )
    }

    /** Length-prefixed fields prevent ambiguous concatenations in integrity checks. */
    fun hash(vararg fields: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun encode(block: DataOutputStream.() -> Unit): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeInt(VERSION); it.block() }
        return Base64.getEncoder().encodeToString(bytes.toByteArray()).also {
            require(it.length <= MAX_ENCODED_LENGTH) { "journal_payload_too_large" }
        }
    }

    private fun <T> decode(encoded: String, block: DataInputStream.() -> T): T {
        require(encoded.length <= MAX_ENCODED_LENGTH) { "journal_payload_too_large" }
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use {
            require(it.readInt() == VERSION) { "unsupported_journal_codec" }
            val result = it.block()
            require(it.available() == 0) { "trailing_journal_bytes" }
            return result
        }
    }

    private fun DataOutputStream.nullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }
    private fun DataInputStream.nullableString(): String? = if (readBoolean()) readUTF() else null

    private fun DataOutputStream.context(value: RecoveryContext?) {
        writeBoolean(value != null)
        if (value != null) {
            writeUTF(value.taskRuleId)
            writeInt(value.cognitiveDifficulty)
            writeInt(value.sensoryLoad)
            writeInt(value.responseWindowMs)
            writeUTF(value.stimulusDistributionId)
            writeUTF(value.cueCondition)
        }
    }
    private fun DataInputStream.context(): RecoveryContext? = if (readBoolean()) {
        RecoveryContext(readUTF(), readInt(), readInt(), readInt(), readUTF(), readUTF())
    } else null
}
