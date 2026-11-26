package pl.edu.prz.arcadhd.task

/** Session-local cosmetic achievements; never use these as study endpoints. */
enum class MissionKeepsake(val title: String, val description: String, val threshold: Int) {
    FIRST_SIGNALS("Odkrywca sygnałów", "Dwie bramy za Tobą.", 2),
    FIRST_ROUTE("Pierwsza trasa", "Cztery bramy za Tobą.", 4),
    WHOLE_ROUTE("Cała droga", "Osiem bram za Tobą.", 8),
}

fun missionKeepsakes(completedTrials: Int): Set<MissionKeepsake> {
    require(completedTrials in 0..SignalGatePreview.TRIAL_COUNT)
    return MissionKeepsake.entries.filter { completedTrials >= it.threshold }.toSet()
}
