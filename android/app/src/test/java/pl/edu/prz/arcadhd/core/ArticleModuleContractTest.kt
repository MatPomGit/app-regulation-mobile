package pl.edu.prz.arcadhd.core

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.edu.prz.arcadhd.variant.CurrentArticleModule

class ArticleModuleContractTest {
    @Test
    fun currentArticleModuleMatchesSelectedFlavor() {
        val problems = VariantModuleContract.validate(
            variant = StudyVariant.current(),
            module = CurrentArticleModule,
        )
        assertTrue(problems.joinToString(separator = "\n"), problems.isEmpty())
    }
}
