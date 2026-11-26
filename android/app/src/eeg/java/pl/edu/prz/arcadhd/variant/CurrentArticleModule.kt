package pl.edu.prz.arcadhd.variant

import android.Manifest
import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.ModuleCapability
import pl.edu.prz.arcadhd.core.ModuleStatus

object CurrentArticleModule : ArticleModule {
    override val paperId = "P5"
    override val title = "Muse / EEG observational companion study"
    override val purpose = "Add wearable discovery, synchronization and event-locked EEG acquisition without feeding EEG into the adaptive controller."
    override val capabilities = listOf(
        ModuleCapability("ble_scan", "BLE device discovery", required = true, runtimePermission = Manifest.permission.BLUETOOTH_SCAN),
        ModuleCapability("ble_connect", "BLE device connection", required = true, runtimePermission = Manifest.permission.BLUETOOTH_CONNECT),
        ModuleCapability("clock_sync", "Peripheral/app clock synchronization boundary", required = true),
        ModuleCapability("raw_eeg_adapter", "Vendor-authorized EEG acquisition adapter", required = false),
    )
    override val measurementOutputs = listOf(
        "peripheral_connection_state",
        "clock_sync_event",
        "eeg_sample_batch",
        "eeg_artifact_marker",
        "eeg_missingness",
    )
    override val implementationStatus = ModuleStatus.SCAFFOLD
}
