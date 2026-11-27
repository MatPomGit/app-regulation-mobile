pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "pinnedLocalLibraries"
                    url = uri(rootDir.resolve(".dependencies/lib-adapt/build/repository"))
                }
            }
            filter { includeModule("org.matpomgit", "lib-adapt") }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "pinnedAudio"
                    url = uri(rootDir.resolve(".dependencies/lib-audio/build/repository"))
                }
            }
            filter { includeModule("org.matpomgit", "lib-audio-stereo") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "ArcAdhd"
include(":app")
