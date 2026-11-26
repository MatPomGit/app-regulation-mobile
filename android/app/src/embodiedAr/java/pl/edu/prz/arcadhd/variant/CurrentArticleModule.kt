package pl.edu.prz.arcadhd.variant

import android.Manifest
import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.ModuleCapability
import pl.edu.prz.arcadhd.core.ModuleStatus

object CurrentArticleModule : ArticleModule {
    override val paperId = "P4"
    override val title = "Embodied and augmented-reality companion study"
    override val purpose = "Support matched stationary, embodied non-AR and embodied AR conditions while preserving the same core task semantics."
    override val capabilities = listOf(
        ModuleCapability("camera_rear", "Rear camera", required = true, runtimePermission = Manifest.permission.CAMERA),
        ModuleCapability("imu", "Motion/orientation sensors", required = true),
        ModuleCapability("arcore", "ARCore session boundary", required = true),
        ModuleCapability("tracking_quality", "AR tracking-quality monitoring", required = true),
    )
    override val measurementOutputs = listOf(
        "movement_feature",
        "orientation_feature",
        "ar_tracking_state",
        "ar_anchor_event",
        "ar_safety_interruption",
    )
    override val implementationStatus = ModuleStatus.SCAFFOLD
}
