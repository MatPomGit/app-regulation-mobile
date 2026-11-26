package pl.edu.prz.arcadhd.core

/** Common invariants that every article source set must satisfy. */
object VariantModuleContract {
    fun validate(variant: StudyVariant, module: ArticleModule): List<String> {
        val problems = mutableListOf<String>()
        if (variant.paperId != module.paperId) {
            problems += "Build paper ID ${variant.paperId} does not match module ${module.paperId}."
        }
        if (module.capabilities.map { it.id }.distinct().size != module.capabilities.size) {
            problems += "Capability IDs must be unique."
        }
        if (module.measurementOutputs.any { it.isBlank() }) {
            problems += "Measurement output identifiers must not be blank."
        }
        return problems
    }
}
