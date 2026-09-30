package vn.gotunhien.keyboard.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TranslationPipelineTest {
    @Test
    fun translatesWithMlKitBeforePassingTheOriginalAndDraftToTheLocalEditor() {
        val calls = mutableListOf<String>()
        val source = "tôi có 2 quốc tịch việt và nga"
        val draft = "I have two Vietnamese nationalities."

        val result = TranslationPipeline.translate(
            sourceText = source,
            translateBase = { text ->
                calls += "mlkit"
                assertEquals(source, text)
                draft
            },
            shouldNaturalize = { calls += "model-check"; true },
            naturalize = { original, base ->
                calls += "local-model"
                assertEquals(source, original)
                assertEquals(draft, base)
                "I have both Vietnamese and Russian citizenship."
            },
        )

        assertEquals(
            TranslationCandidates(draft, "I have both Vietnamese and Russian citizenship."),
            result,
        )
        assertEquals(listOf("mlkit", "model-check", "local-model"), calls)
    }

    @Test
    fun returnsMlKitTranslationWhenTheLocalEditorIsUnavailable() {
        val calls = mutableListOf<String>()

        val result = TranslationPipeline.translate(
            sourceText = "xin chào",
            translateBase = { calls += "mlkit"; "Hello." },
            shouldNaturalize = { calls += "model-check"; false },
            naturalize = { _, _ -> calls += "local-model"; "Hey!" },
        )

        assertEquals(TranslationCandidates("Hello.", "Hello."), result)
        assertEquals(listOf("mlkit", "model-check"), calls)
    }

    @Test
    fun fallsBackToMlKitWhenTheLocalEditorFailsOrReturnsBlank() {
        val draft = "I have both Vietnamese and Russian citizenship."

        val blankResult = TranslationPipeline.translate(
            sourceText = "source",
            translateBase = { draft },
            shouldNaturalize = { true },
            naturalize = { _, _ -> "  " },
        )
        val failedResult = TranslationPipeline.translate(
            sourceText = "source",
            translateBase = { draft },
            shouldNaturalize = { true },
            naturalize = { _, _ -> error("local model unavailable") },
        )

        assertEquals(TranslationCandidates(draft, draft), blankResult)
        assertEquals(TranslationCandidates(draft, draft), failedResult)
    }

    @Test
    fun rejectsAnEmptyMlKitTranslationInsteadOfInventingAnOutput() {
        assertThrows(IllegalStateException::class.java) {
            TranslationPipeline.translate(
                sourceText = "source",
                translateBase = { " " },
                shouldNaturalize = { true },
                naturalize = { _, _ -> "invented" },
            )
        }
    }
}
