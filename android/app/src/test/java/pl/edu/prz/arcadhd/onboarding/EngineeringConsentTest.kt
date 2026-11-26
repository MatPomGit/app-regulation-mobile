package pl.edu.prz.arcadhd.onboarding

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import pl.edu.prz.arcadhd.variant.CurrentArticleModule

class EngineeringConsentTest {
    @Before fun reset() { ProcessDemoAgreement.withdraw() }
    @After fun cleanup() { ProcessDemoAgreement.withdraw() }

    @Test fun noImplicitAgreementAndIncompleteChoicesNeverOpenTheGate() {
        assertNull(ProcessDemoAgreement.current("P1"))
        val adult = ConsentChoices(DemoRole.ADULT, true, true)
        listOf(
            ConsentChoices(), adult.copy(role = null),
            adult.copy(informationUnderstood = false), adult.copy(localStorageAccepted = false),
        ).forEach {
            assertFalse(ProcessDemoAgreement.accept(it, "P1"))
            assertNull(ProcessDemoAgreement.current("P1"))
        }
    }

    @Test fun caregiverRequiresChildWillingnessSeparately() {
        val parent = ConsentChoices(DemoRole.CAREGIVER, true, true)
        assertFalse(ProcessDemoAgreement.accept(parent, "P2"))
        assertTrue(ProcessDemoAgreement.accept(parent.copy(childWilling = true), "P2"))
        assertEquals(DemoRole.CAREGIVER, ProcessDemoAgreement.current("P2")!!.role)
    }

    @Test fun AgreementCannotTransferAcrossVariantsOrInformationVersions() {
        val adult = ConsentChoices(DemoRole.ADULT, true, true)
        assertTrue(ProcessDemoAgreement.accept(adult, "P1"))
        assertNull(ProcessDemoAgreement.current("P2"))
        val saved = ProcessDemoAgreement.current("P1")!!
        assertFalse(saved.copy(informationVersion = "outdated").allows("P1"))
        assertFalse(ProcessDemoAgreement.accept(adult, "unknown"))
        assertNull(ProcessDemoAgreement.current("unknown"))
    }

    @Test fun withdrawalBlocksFurtherUseAndRequiresANewExplicitDecision() {
        val adult = ConsentChoices(DemoRole.ADULT, true, true)
        assertTrue(ProcessDemoAgreement.accept(adult, "P5"))
        ProcessDemoAgreement.withdraw()
        assertNull(ProcessDemoAgreement.current("P5"))
        assertFalse(ProcessDemoAgreement.accept(ConsentChoices(), "P5"))
        assertTrue(ProcessDemoAgreement.accept(adult, "P5"))
    }

    @Test fun eachVariantHasItsOwnGuideAndTheSelectedFlavorResolvesExactly() {
        val guides = VariantGuides.paperIds.map(VariantGuides::forPaper)
        assertEquals(5, guides.map { it.title }.distinct().size)
        assertEquals(5, guides.map { guide -> guide.sections.single {
            it.title == "Instrukcja modułu dodatkowego"
        }.paragraphs }.distinct().size)
        val selected = VariantGuides.forPaper(CurrentArticleModule.paperId)
        assertEquals(CurrentArticleModule.paperId, selected.paperId)
        assertTrue(selected.sections.all { it.paragraphs.isNotEmpty() && it.paragraphs.all(String::isNotBlank) })
        try {
            VariantGuides.forPaper("unknown")
            fail("Unknown flavor must not silently use a different guide")
        } catch (_: IllegalArgumentException) { }
    }
}
