package pl.edu.prz.arcadhd.onboarding

enum class DemoRole { ADULT, CAREGIVER }

data class ConsentChoices(
    val role: DemoRole? = null,
    val informationUnderstood: Boolean = false,
    val localStorageAccepted: Boolean = false,
    val childWilling: Boolean = false,
) {
    val canAccept: Boolean get() = role != null && informationUnderstood && localStorageAccepted &&
        (role != DemoRole.CAREGIVER || childWilling)

    fun accept(paperId: String): DemoAgreement? = if (canAccept && paperId in VariantGuides.paperIds)
        DemoAgreement(paperId, VariantGuides.INFORMATION_VERSION, requireNotNull(role)) else null
}

/** Technical preview agreement, not a research consent, signature or participant record. */
data class DemoAgreement(val paperId: String, val informationVersion: String, val role: DemoRole) {
    fun allows(paperId: String): Boolean = this.paperId == paperId && paperId in VariantGuides.paperIds &&
        informationVersion == VariantGuides.INFORMATION_VERSION
}

/** No disk receipt or identifier: fresh decision after process death; preserved across rotation. */
object ProcessDemoAgreement {
    private var agreement: DemoAgreement? = null
    fun current(paperId: String): DemoAgreement? = agreement?.takeIf { it.allows(paperId) }
    fun accept(choices: ConsentChoices, paperId: String): Boolean {
        val accepted = choices.accept(paperId) ?: return false
        agreement = accepted
        return true
    }
    fun withdraw() { agreement = null }
}
