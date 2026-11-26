package pl.edu.prz.arcadhd.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyVariantTest {
    @Test
    fun currentBuildEnablesAtMostOneCompanionModule() {
        val variant = StudyVariant.current()
        val enabled = listOf(
            variant.facialGazeEnabled,
            variant.bilateralAudioEnabled,
            variant.embodiedArEnabled,
            variant.eegEnabled,
        ).count { it }

        assertTrue("Only one companion module may be enabled per article build", enabled <= 1)
    }

    @Test
    fun paperIdentityMatchesCompanionModule() {
        val variant = StudyVariant.current()
        val expected = when {
            variant.facialGazeEnabled -> "P2"
            variant.bilateralAudioEnabled -> "P3"
            variant.embodiedArEnabled -> "P4"
            variant.eegEnabled -> "P5"
            else -> "P1"
        }

        assertEquals(expected, variant.paperId)
    }
}
