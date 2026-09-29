package vn.gotunhien.keyboard.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TelexComposerTest {
    @Test
    fun convertsVowelAndConsonantModifiers() {
        assertEquals("â", TelexComposer.append("a", 'a'))
        assertEquals("ă", TelexComposer.append("a", 'w'))
        assertEquals("ê", TelexComposer.append("e", 'e'))
        assertEquals("ô", TelexComposer.append("o", 'o'))
        assertEquals("ơ", TelexComposer.append("o", 'w'))
        assertEquals("ư", TelexComposer.append("u", 'w'))
        assertEquals("đ", TelexComposer.append("d", 'd'))
    }

    @Test
    fun appliesToneToTheVowelInTheVietnameseNucleus() {
        assertEquals("bạn", TelexComposer.append("ban", 'j'))
        assertEquals("chào", TelexComposer.append("chao", 'f'))
        assertEquals("xín", TelexComposer.append("xin", 's'))
    }

    @Test
    fun repeatingTheToneKeyRemovesThatTone() {
        assertEquals("ban", TelexComposer.append("bạn", 'j'))
    }

    @Test
    fun preservesCaseAndPunctuation() {
        assertEquals("Đ", TelexComposer.append("D", 'D'))
    }
}
