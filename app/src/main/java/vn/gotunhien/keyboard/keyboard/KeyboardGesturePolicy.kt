package vn.gotunhien.keyboard.keyboard

internal object KeyboardGesturePolicy {
    private val alternates = mapOf(
        'a' to "áàảãạâă",
        'e' to "éèẻẽẹê",
        'i' to "íìỉĩị",
        'o' to "óòỏõọôơ",
        'u' to "úùủũụư",
        'y' to "ýỳỷỹỵ",
        'd' to "đ",
    )

    fun cursorSteps(deltaPx: Float, stepPx: Float): Int {
        if (!deltaPx.isFinite() || !stepPx.isFinite() || stepPx <= 0f) return 0
        val steps = (kotlin.math.abs(deltaPx) / stepPx).toInt()
        return if (deltaPx < 0f) -steps else steps
    }

    fun isHorizontalSwipe(deltaX: Float, deltaY: Float, thresholdPx: Float): Boolean =
        deltaX.isFinite() && deltaY.isFinite() && thresholdPx >= 0f &&
            kotlin.math.abs(deltaX) >= thresholdPx && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.2f

    fun isDeleteWordSwipe(deltaX: Float, deltaY: Float, thresholdPx: Float): Boolean =
        deltaX < 0f && isHorizontalSwipe(deltaX, deltaY, thresholdPx)

    fun deleteWordCodePointCount(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 0

        var offset = textBeforeCursor.length
        val deleteWhitespace = Character.isWhitespace(textBeforeCursor.codePointBefore(offset))
        var count = 0
        while (offset > 0) {
            val codePoint = textBeforeCursor.codePointBefore(offset)
            if (Character.isWhitespace(codePoint) != deleteWhitespace) break
            offset -= Character.charCount(codePoint)
            count += 1
        }
        return count
    }

    fun alternateCharacters(character: Char): List<Char> =
        alternates[character.lowercaseChar()]
            ?.map { alternate -> if (character.isUpperCase()) alternate.uppercaseChar() else alternate }
            .orEmpty()
}
