package pl.edu.prz.arcadhd.variant

import android.Manifest
import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.ModuleCapability
import pl.edu.prz.arcadhd.core.ModuleStatus

object CurrentArticleModule : ArticleModule {
    override val paperId = "P2"
    override val title = "Facial behaviour and gaze companion study"
    override val purpose = "Add on-device front-camera derived facial/gaze features to the unchanged core loop."
    override val capabilities = listOf(
        ModuleCapability("camera_front", "Front camera", required = true, runtimePermission = Manifest.permission.CAMERA),
        ModuleCapability("face_landmarks", "On-device face landmark inference", required = true),
        ModuleCapability("gaze_candidate", "Experimental gaze feature extraction", required = false),
    )
    override val measurementOutputs = listOf(
        "vision_frame_quality",
        "face_tracking_quality",
        "facial_feature_window",
        "gaze_feature_window",
        "vision_missingness",
    )
    override val implementationStatus = ModuleStatus.SCAFFOLD
}
