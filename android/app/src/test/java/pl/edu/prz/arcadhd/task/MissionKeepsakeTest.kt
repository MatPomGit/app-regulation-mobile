package pl.edu.prz.arcadhd.task

import org.junit.Assert.*
import org.junit.Test

class MissionKeepsakeTest {
    @Test fun keepsakesUnlockOnlyAtCompletedTrialMilestones() {
        assertTrue(missionKeepsakes(0).isEmpty())
        assertTrue(missionKeepsakes(1).isEmpty())
        assertEquals(setOf(MissionKeepsake.FIRST_SIGNALS), missionKeepsakes(2))
        assertEquals(setOf(MissionKeepsake.FIRST_SIGNALS), missionKeepsakes(3))
        assertEquals(2, missionKeepsakes(4).size)
        assertEquals(MissionKeepsake.entries.toSet(), missionKeepsakes(8))
    }

    @Test fun progressNeverRevokesAnUnlockedKeepsake() {
        var album = emptySet<MissionKeepsake>()
        listOf(2, 4, 4, 0, 1, 8).forEach {
            val previous = album
            album = album + missionKeepsakes(it)
            assertTrue(album.containsAll(previous))
        }
        assertEquals(MissionKeepsake.entries.toSet(), album)
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleProgressIsRejected() { missionKeepsakes(9) }
}
