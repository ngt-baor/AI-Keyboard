package vn.gotunhien.keyboard.translation

internal data class TranslationCandidates(
    val baseTranslation: String,
    val naturalizedTranslation: String,
)

internal object TranslationPipeline {
    fun translate(
        sourceText: String,
        translateBase: (String) -> String,
        shouldNaturalize: () -> Boolean,
        naturalize: (sourceText: String, baseTranslation: String) -> String,
    ): TranslationCandidates {
        val baseTranslation = translateBase(sourceText).trim()
        check(baseTranslation.isNotEmpty()) { "empty_base_translation" }
        val canNaturalize = try {
            shouldNaturalize()
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
        if (!canNaturalize) return TranslationCandidates(baseTranslation, baseTranslation)

        val naturalizedTranslation = try {
            naturalize(sourceText, baseTranslation).trim().ifEmpty { baseTranslation }
        } catch (_: Exception) {
            baseTranslation
        } catch (_: LinkageError) {
            baseTranslation
        }
        return TranslationCandidates(baseTranslation, naturalizedTranslation)
    }
}
