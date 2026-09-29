package vn.gotunhien.keyboard.translation

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPolicyTest {
    @Test
    fun refusesSensitiveAndNonTextFields() {
        assertFalse(TranslationPolicy.mayTranslate(InputType.TYPE_CLASS_NUMBER))
        assertFalse(TranslationPolicy.mayTranslate(InputType.TYPE_CLASS_PHONE))
        assertFalse(TranslationPolicy.mayTranslate(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(TranslationPolicy.mayTranslate(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(TranslationPolicy.mayTranslate(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
    }

    @Test
    fun onlyAppliesAUsablePreviewToTheDraftItWasMadeFrom() {
        assertTrue(TranslationPolicy.canApply("xin chào", "xin chào", "Hey!"))
        assertFalse(TranslationPolicy.canApply("xin chào nhé", "xin chào", "Hey!"))
        assertFalse(TranslationPolicy.canApply("xin chào", "xin chào", "  "))
    }

    @Test
    fun toneInstructionsUseNaturalCasualTargetLanguage() {
        assertTrue(TranslationPolicy.systemInstruction(TranslationTarget.ENGLISH).contains("casual English"))
        assertTrue(TranslationPolicy.systemInstruction(TranslationTarget.RUSSIAN).contains("casual Russian"))
    }

    @Test
    fun englishInstructionPreservesEveryCountryInNationalityLists() {
        val instruction = TranslationPolicy.systemInstruction(TranslationTarget.ENGLISH)

        assertTrue(instruction.contains("Keep every country and keep first person."))
        assertTrue(instruction.contains("Tôi có quốc tịch Việt và Nga."))
        assertTrue(instruction.contains("I have both Vietnamese and Russian citizenship."))
    }
}
