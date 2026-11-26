package pl.edu.prz.arcadhd.variant

import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.ModuleCapability
import pl.edu.prz.arcadhd.core.ModuleStatus

object CurrentArticleModule : ArticleModule {
    override val paperId = "P1"
    override val title = "Core closed-loop regulation"
    override val purpose = "Deliver the shared challenge-regulation-recovery loop without companion sensing modules."
    override val capabilities = listOf(
        ModuleCapability("task", "Signal Gate task shell", required = true),
        ModuleCapability("telemetry", "Offline-first telemetry", required = true),
        ModuleCapability("haptics", "Bounded haptic cues", required = false),
        ModuleCapability("audio", "Ordinary functional audio", required = false),
    )
    override val measurementOutputs = listOf(
        "task_trial",
        "challenge_event",
        "regulation_transition",
        "recovery_event",
        "technical_interruption",
    )
    override val implementationStatus = ModuleStatus.SCAFFOLD
}
