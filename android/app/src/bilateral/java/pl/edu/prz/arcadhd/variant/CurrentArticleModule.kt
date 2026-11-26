package pl.edu.prz.arcadhd.variant

import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.ModuleCapability
import pl.edu.prz.arcadhd.core.ModuleStatus

object CurrentArticleModule : ArticleModule {
    override val paperId = "P3"
    override val title = "Bilateral auditory stimulation companion study"
    override val purpose = "Add controlled stereo alternation and acoustically matched comparator conditions to the shared core shell."
    override val capabilities = listOf(
        ModuleCapability("stereo_audio", "Stereo audio output", required = true),
        ModuleCapability("channel_check", "Left/right channel verification", required = true),
        ModuleCapability("route_monitoring", "Audio route monitoring", required = true),
    )
    override val measurementOutputs = listOf(
        "audio_condition",
        "audio_route",
        "nominal_channel_switch",
        "software_audio_timestamp",
        "audio_interruption",
    )
    override val implementationStatus = ModuleStatus.SCAFFOLD
}
