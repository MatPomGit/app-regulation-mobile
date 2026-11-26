package pl.edu.prz.arcadhd.audio

/** Route classification reflects Android device metadata, not acoustic verification. */
enum class EngineeringRouteKind {
    WIRED_HEADPHONES,
    WIRED_HEADSET,
    USB_HEADSET,
    BLE_HEADSET,
    BLUETOOTH_A2DP,
    OTHER,
}

data class EngineeringAudioRoute(
    val deviceId: Int,
    val kind: EngineeringRouteKind,
    val isOutput: Boolean,
    val reportsStereo: Boolean,
)

/** Fail closed if the route is missing, ambiguous, changed or potentially a speaker. */
object EngineeringAudioRoutePolicy {
    fun accepts(route: EngineeringAudioRoute?): Boolean = route != null &&
        route.deviceId >= 0 && route.isOutput && route.reportsStereo &&
        route.kind in setOf(
            EngineeringRouteKind.WIRED_HEADPHONES,
            EngineeringRouteKind.WIRED_HEADSET,
            EngineeringRouteKind.USB_HEADSET,
            EngineeringRouteKind.BLE_HEADSET,
        )

    fun unchanged(initial: EngineeringAudioRoute, current: EngineeringAudioRoute?): Boolean =
        accepts(initial) && accepts(current) && current == initial
}
