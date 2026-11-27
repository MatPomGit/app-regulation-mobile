plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val researchKeystorePath = System.getenv("ARC_ADHD_KEYSTORE_PATH")
val researchKeyAlias = System.getenv("ARC_ADHD_KEY_ALIAS")
val researchStorePassword = System.getenv("ARC_ADHD_STORE_PASSWORD")
val researchKeyPassword = System.getenv("ARC_ADHD_KEY_PASSWORD")
val researchSigningAvailable = listOf(
    researchKeystorePath,
    researchKeyAlias,
    researchStorePassword,
    researchKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "pl.edu.prz.arcadhd"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.edu.prz.arcadhd"
        minSdk = 26
        targetSdk = 37
        versionCode = System.getenv("ARC_ADHD_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ARC_ADHD_VERSION_NAME") ?: "0.1.0-dev"
    }

    signingConfigs {
        if (researchSigningAvailable) {
            create("research") {
                storeFile = file(requireNotNull(researchKeystorePath))
                storePassword = requireNotNull(researchStorePassword)
                keyAlias = requireNotNull(researchKeyAlias)
                keyPassword = requireNotNull(researchKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "PARTICIPANT_READY", "false")
        }

        create("researchRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("boolean", "PARTICIPANT_READY", "false")

            if (researchSigningAvailable) {
                signingConfig = signingConfigs.getByName("research")
            }
        }
    }

    flavorDimensions += "study"
    productFlavors {
        create("core") {
            dimension = "study"
            applicationIdSuffix = ".core"
            versionNameSuffix = "-core"
            resValue("string", "app_name", "ARC-ADHD Podstawowy")
            buildConfigField("String", "PAPER_ID", "\"P1\"")
            buildConfigField("boolean", "FACIAL_GAZE_ENABLED", "false")
            buildConfigField("boolean", "BILATERAL_AUDIO_ENABLED", "false")
            buildConfigField("boolean", "EMBODIED_AR_ENABLED", "false")
            buildConfigField("boolean", "EEG_ENABLED", "false")
        }
        create("facial") {
            dimension = "study"
            applicationIdSuffix = ".facial"
            versionNameSuffix = "-facial"
            resValue("string", "app_name", "ARC-ADHD Mimika")
            buildConfigField("String", "PAPER_ID", "\"P2\"")
            buildConfigField("boolean", "FACIAL_GAZE_ENABLED", "true")
            buildConfigField("boolean", "BILATERAL_AUDIO_ENABLED", "false")
            buildConfigField("boolean", "EMBODIED_AR_ENABLED", "false")
            buildConfigField("boolean", "EEG_ENABLED", "false")
        }
        create("bilateral") {
            dimension = "study"
            applicationIdSuffix = ".bilateral"
            versionNameSuffix = "-bilateral"
            resValue("string", "app_name", "ARC-ADHD Dźwięk")
            buildConfigField("String", "PAPER_ID", "\"P3\"")
            buildConfigField("boolean", "FACIAL_GAZE_ENABLED", "false")
            buildConfigField("boolean", "BILATERAL_AUDIO_ENABLED", "true")
            buildConfigField("boolean", "EMBODIED_AR_ENABLED", "false")
            buildConfigField("boolean", "EEG_ENABLED", "false")
        }
        create("embodiedAr") {
            dimension = "study"
            applicationIdSuffix = ".ar"
            versionNameSuffix = "-ar"
            resValue("string", "app_name", "ARC-ADHD Ruch i AR")
            buildConfigField("String", "PAPER_ID", "\"P4\"")
            buildConfigField("boolean", "FACIAL_GAZE_ENABLED", "false")
            buildConfigField("boolean", "BILATERAL_AUDIO_ENABLED", "false")
            buildConfigField("boolean", "EMBODIED_AR_ENABLED", "true")
            buildConfigField("boolean", "EEG_ENABLED", "false")
        }
        create("eeg") {
            dimension = "study"
            applicationIdSuffix = ".eeg"
            versionNameSuffix = "-eeg"
            resValue("string", "app_name", "ARC-ADHD EEG")
            buildConfigField("String", "PAPER_ID", "\"P5\"")
            buildConfigField("boolean", "FACIAL_GAZE_ENABLED", "false")
            buildConfigField("boolean", "BILATERAL_AUDIO_ENABLED", "false")
            buildConfigField("boolean", "EMBODIED_AR_ENABLED", "false")
            buildConfigField("boolean", "EEG_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation("org.matpomgit:lib-adapt:0.1.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    // JVM integration tests only; Android continues to use its built-in SQLite engine.
    testImplementation("org.xerial:sqlite-jdbc:3.54.1.0")
}

