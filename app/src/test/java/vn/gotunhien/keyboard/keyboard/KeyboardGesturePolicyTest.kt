package vn.gotunhien.keyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGesturePolicyTest {
    @Test
    fun cursorMovementUsesWholeDistanceStepsInEitherDirection() {
        assertEquals(2, KeyboardGesturePolicy.cursorSteps(25f, 12f))
        assertEquals(-2, KeyboardGesturePolicy.cursorSteps(-25f, 12f))
        assertEquals(0, KeyboardGesturePolicy.cursorSteps(11f, 12f))
        assertEquals(0, KeyboardGesturePolicy.cursorSteps(25f, 0f))
    }

    @Test
    fun recognizesOnlyMostlyHorizontalSwipesBeyondTheThreshold() {
        assertTrue(KeyboardGesturePolicy.isHorizontalSwipe(-60f, 12f, 48f))
        assertTrue(KeyboardGesturePolicy.isHorizontalSwipe(60f, 12f, 48f))
        assertFalse(KeyboardGesturePolicy.isHorizontalSwipe(-40f, 5f, 48f))
        assertFalse(KeyboardGesturePolicy.isHorizontalSwipe(-60f, 70f, 48f))
        assertTrue(KeyboardGesturePolicy.isDeleteWordSwipe(-60f, 12f, 48f))
        assertFalse(KeyboardGesturePolicy.isDeleteWordSwipe(60f, 12f, 48f))
    }

    @Test
    fun countsThePreviousWhitespaceDelimitedTokenByCodePoint() {
        assertEquals(5, KeyboardGesturePolicy.deleteWordCodePointCount("hello world"))
        assertEquals(3, KeyboardGesturePolicy.deleteWordCodePointCount("word   "))
        assertEquals(1, KeyboardGesturePolicy.deleteWordCodePointCount("word "))
        assertEquals(3, KeyboardGesturePolicy.deleteWordCodePointCount("hi😀"))
        assertEquals(0, KeyboardGesturePolicy.deleteWordCodePointCount(""))
    }

    @Test
    fun providesVietnameseAlternateCharactersAndPreservesCase() {
        assertEquals(listOf('á', 'à', 'ả', 'ã', 'ạ', 'â', 'ă'), KeyboardGesturePolicy.alternateCharacters('a'))
        assertEquals(listOf('Á', 'À', 'Ả', 'Ã', 'Ạ', 'Â', 'Ă'), KeyboardGesturePolicy.alternateCharacters('A'))
        assertEquals(listOf('đ'), KeyboardGesturePolicy.alternateCharacters('d'))
        assertEquals(listOf('Đ'), KeyboardGesturePolicy.alternateCharacters('D'))
        assertTrue(KeyboardGesturePolicy.alternateCharacters('q').isEmpty())
    }
}
