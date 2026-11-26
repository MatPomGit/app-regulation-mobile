package pl.edu.prz.arcadhd.storage

import java.security.MessageDigest

/**
 * Immutable engineering-only record of one completed Signal Gate trial.
 *
 * It deliberately contains no participant identifier, device identifier or export envelope.
 */
data class EngineeringTrialRecord(
    val taskRunId: String,
    val trialId: String,
    val trialIndex: Int,
    val ruleId: String,
    val stimulusId: String,
    val responseKind: String,
    val expectedGo: Boolean,
    val correct: Boolean,
    val shownAtMs: Long,
    val respondedAtMs: Long,
) {
    val latencyMs: Long get() = respondedAtMs - shownAtMs

    init {
        require(taskRunId.isNotBlank())
        require(trialId.isNotBlank())
        require(trialIndex >= 0)
        require(ruleId.isNotBlank() && stimulusId.isNotBlank() && responseKind.isNotBlank())
        require(shownAtMs >= 0)
        require(respondedAtMs >= shownAtMs)
    }

    fun payloadHash(): String {
        val fields = listOf(
            taskRunId,
            trialId,
            trialIndex.toString(),
            ruleId,
            stimulusId,
            responseKind,
            expectedGo.toString(),
            correct.toString(),
            shownAtMs.toString(),
            respondedAtMs.toString(),
            latencyMs.toString(),
        )
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
