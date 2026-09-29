package vn.gotunhien.keyboard.input

import java.text.Normalizer

internal object TelexComposer {
    private val toneMarks = mapOf(
        's' to '\u0301',
        'f' to '\u0300',
        'r' to '\u0309',
        'x' to '\u0303',
        'j' to '\u0323',
    )

    fun append(word: String, key: Char): String {
        val keyLower = key.lowercaseChar()
        val toneMark = toneMarks[keyLower]
        if (toneMark != null) return applyTone(word, toneMark, key)

        if (word.isEmpty()) return key.toString()
        val previous = word.last()
        val previousLower = previous.lowercaseChar()
        val modified = when {
            keyLower == 'a' && previousLower == 'a' -> addShape(previous, '\u0302')
            keyLower == 'e' && previousLower == 'e' -> addShape(previous, '\u0302')
            keyLower == 'o' && previousLower == 'o' -> addShape(previous, '\u0302')
            keyLower == 'w' && previousLower == 'a' -> addShape(previous, '\u0306')
            keyLower == 'w' && previousLower == 'o' -> addShape(previous, '\u031b')
            keyLower == 'w' && previousLower == 'u' -> addShape(previous, '\u031b')
            keyLower == 'd' && previousLower == 'd' -> if (previous.isUpperCase() || key.isUpperCase()) 'Đ' else 'đ'
            else -> null
        } ?: return word + key

        return word.dropLast(1) + modified
    }

    private fun applyTone(word: String, toneMark: Char, typedKey: Char): String {
        val indices = word.indices.filter { isVietnameseVowel(word[it]) && !isGlide(word, it) }
        if (indices.isEmpty()) return word + typedKey

        val lastGroup = mutableListOf<Int>()
        for (index in indices) {
            if (lastGroup.isEmpty() || index == lastGroup.last() + 1) {
                lastGroup += index
            } else {
                lastGroup.clear()
                lastGroup += index
            }
        }

        val selected = when (lastGroup.size) {
            1 -> lastGroup[0]
            2 -> {
                val pair = "${baseVowel(word[lastGroup[0]])}${baseVowel(word[lastGroup[1]])}"
                if (pair in setOf("ie", "uo")) lastGroup[1] else lastGroup[0]
            }
            else -> lastGroup[lastGroup.size / 2]
        }

        val old = word[selected]
        val existingTone = toneOf(old)
        val replacement = if (existingTone == toneMark) removeTone(old) else withTone(old, toneMark)
        return word.replaceRange(selected, selected + 1, replacement.toString())
    }

    private fun isGlide(word: String, index: Int): Boolean {
        val letter = word[index].lowercaseChar()
        if (index == 1 && word[0].lowercaseChar() == 'q' && letter == 'u') return true
        if (index == 1 && word.startsWith("gi", ignoreCase = true) && letter == 'i') {
            return word.drop(2).firstOrNull()?.let(::isVietnameseVowel) == true
        }
        return false
    }

    private fun isVietnameseVowel(char: Char): Boolean = baseVowel(char) in "aeiouy"

    private fun baseVowel(char: Char): Char = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        .firstOrNull()
        ?.lowercaseChar()
        ?: char.lowercaseChar()

    private fun addShape(char: Char, shapeMark: Char): Char {
        val decomposed = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        val base = decomposed.first()
        val marks = decomposed.drop(1).filterNot(::isToneMark).toMutableList()
        if (shapeMark !in marks) marks += shapeMark
        val normalized = Normalizer.normalize(base + marks.joinToString(""), Normalizer.Form.NFC).single()
        return if (char.isUpperCase()) normalized.uppercaseChar() else normalized
    }

    private fun withTone(char: Char, toneMark: Char): Char {
        val decomposed = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        val base = decomposed.first()
        val marks = decomposed.drop(1).filterNot(::isToneMark)
        val normalized = Normalizer.normalize(base + marks + toneMark, Normalizer.Form.NFC).single()
        return if (char.isUpperCase()) normalized.uppercaseChar() else normalized
    }

    private fun removeTone(char: Char): Char {
        val decomposed = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        val base = decomposed.first()
        val marks = decomposed.drop(1).filterNot(::isToneMark)
        val normalized = Normalizer.normalize(base + marks, Normalizer.Form.NFC).single()
        return if (char.isUpperCase()) normalized.uppercaseChar() else normalized
    }

    private fun toneOf(char: Char): Char? = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        .drop(1)
        .firstOrNull(::isToneMark)

    private fun isToneMark(char: Char): Boolean = char in setOf('\u0300', '\u0301', '\u0303', '\u0309', '\u0323')
}
