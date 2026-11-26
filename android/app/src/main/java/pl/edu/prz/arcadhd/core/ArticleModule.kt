package pl.edu.prz.arcadhd.core

/**
 * Compile-time article-specific module descriptor.
 *
 * This is an engineering contract only. Availability does not imply that a
 * capability has been scientifically validated or approved for participant use.
 */
interface ArticleModule {
    val paperId: String
    val title: String
    val purpose: String
    val capabilities: List<ModuleCapability>
    val measurementOutputs: List<String>
    val implementationStatus: ModuleStatus
}

data class ModuleCapability(
    val id: String,
    val label: String,
    val required: Boolean,
    val runtimePermission: String? = null,
)

enum class ModuleStatus {
    SCAFFOLD,
    ENGINEERING_PROTOTYPE,
    QUALIFIED_FOR_ENGINEERING,
}
