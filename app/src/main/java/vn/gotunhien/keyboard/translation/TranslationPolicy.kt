package vn.gotunhien.keyboard.translation

import android.text.InputType

internal enum class TranslationTarget {
    ENGLISH,
    RUSSIAN,
}

internal object TranslationPolicy {
    const val MAX_DRAFT_LENGTH = 1_000

    fun mayTranslate(inputType: Int): Boolean {
        val fieldClass = inputType and InputType.TYPE_MASK_CLASS
        if (fieldClass != InputType.TYPE_CLASS_TEXT) return false

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    fun canApply(currentDraft: String, sourceDraft: String, translatedText: String): Boolean =
        currentDraft == sourceDraft && translatedText.isNotBlank()

    fun systemInstruction(target: TranslationTarget): String {
        val targetDescription = when (target) {
            TranslationTarget.ENGLISH -> "casual English"
            TranslationTarget.RUSSIAN -> "casual Russian"
        }
        val outputLanguage = if (target == TranslationTarget.ENGLISH) "English" else "Russian"
        val example = if (target == TranslationTarget.ENGLISH) {
            "Example: “Tôi có quốc tịch Việt và Nga.” → “I have both Vietnamese and Russian citizenship.”"
        } else {
            ""
        }

        return buildString {
            appendLine("Translate Vietnamese chat messages into $targetDescription.")
            appendLine("Keep every country and keep first person.")
            if (example.isNotEmpty()) {
                appendLine(example)
            }
            append("Now translate the message. Return exactly one $outputLanguage sentence, with no notes.")
        }
    }
}
