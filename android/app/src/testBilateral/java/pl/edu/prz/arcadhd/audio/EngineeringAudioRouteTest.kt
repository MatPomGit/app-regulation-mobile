package pl.edu.prz.arcadhd.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringAudioRouteTest {
    private val wired = EngineeringAudioRoute(
        3, EngineeringRouteKind.WIRED_HEADPHONES, isOutput = true, reportsStereo = true,
    )

    @Test fun permitsKnownStereoHeadphoneRoutes() {
        listOf(
            EngineeringRouteKind.WIRED_HEADPHONES,
            EngineeringRouteKind.WIRED_HEADSET,
            EngineeringRouteKind.USB_HEADSET,
            EngineeringRouteKind.BLE_HEADSET,
        ).forEach { assertTrue(EngineeringAudioRoutePolicy.accepts(wired.copy(kind = it))) }
    }

    @Test fun rejectsUnknownMonoInputAndSpeakerAmbiguity() {
        assertFalse(EngineeringAudioRoutePolicy.accepts(null))
        assertFalse(EngineeringAudioRoutePolicy.accepts(wired.copy(isOutput = false)))
        assertFalse(EngineeringAudioRoutePolicy.accepts(wired.copy(reportsStereo = false)))
        assertFalse(EngineeringAudioRoutePolicy.accepts(wired.copy(deviceId = -1)))
        assertFalse(EngineeringAudioRoutePolicy.accepts(wired.copy(kind = EngineeringRouteKind.OTHER)))
        assertFalse(EngineeringAudioRoutePolicy.accepts(
            wired.copy(kind = EngineeringRouteKind.BLUETOOTH_A2DP),
        ))
    }

    @Test fun rejectsRouteChangesEvenToAnotherAllowedDevice() {
        assertTrue(EngineeringAudioRoutePolicy.unchanged(wired, wired.copy()))
        assertFalse(EngineeringAudioRoutePolicy.unchanged(wired, wired.copy(deviceId = 4)))
        assertFalse(EngineeringAudioRoutePolicy.unchanged(wired, wired.copy(reportsStereo = false)))
        assertFalse(EngineeringAudioRoutePolicy.unchanged(wired, null))
    }
}
