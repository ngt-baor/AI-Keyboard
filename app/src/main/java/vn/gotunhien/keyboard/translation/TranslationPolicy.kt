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

        return """Translate Vietnamese chat messages into $targetDescription, as a native friend would text.
Use everyday wording, preserve the original meaning, names, emoji, and punctuation, and do not add facts.
Avoid stiff or overly formal phrasing. Return only the translated message with no quotes or explanation."""
    }
}
