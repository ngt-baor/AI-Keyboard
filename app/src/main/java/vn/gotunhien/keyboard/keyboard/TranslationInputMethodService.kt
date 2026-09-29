package vn.gotunhien.keyboard.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.LinearLayout
import android.widget.TextView
import vn.gotunhien.keyboard.input.TelexComposer
import vn.gotunhien.keyboard.translation.LocalTranslator
import vn.gotunhien.keyboard.translation.TranslationPolicy
import vn.gotunhien.keyboard.translation.TranslationTarget

class TranslationInputMethodService : InputMethodService() {
    private lateinit var translator: LocalTranslator
    private var translationMode = false
    private var vietnameseInput = true
    private var shifted = false
    private var symbols = false
    private var target = TranslationTarget.ENGLISH
    private var translating = false
    private var translationRequestId = 0
    private var serviceDestroyed = false
    private var preview: TranslationPreview? = null
    private var statusMessage: String? = null

    override fun onCreate() {
        super.onCreate()
        serviceDestroyed = false
        translator = LocalTranslator(this)
    }

    override fun onCreateInputView(): View = createKeyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        preview = null
        statusMessage = null
        translating = false
        translationRequestId += 1
    }

    override fun onDestroy() {
        serviceDestroyed = true
        translationRequestId += 1
        if (::translator.isInitialized) translator.close()
        super.onDestroy()
    }

    private fun createKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(7))
            setBackgroundColor(Color.rgb(226, 231, 236))
        }

        val toolbar = row(root, height = 40)
        toolbar.addView(key(if (vietnameseInput) "VI" else "EN", KeyStyle.SPECIAL, 0.8f) {
            vietnameseInput = !vietnameseInput
            statusMessage = null
            redraw()
        })
        toolbar.addView(key(if (translationMode) "Thoát dịch" else "Dịch", KeyStyle.ACCENT, 1.5f) {
            translationMode = !translationMode
            if (!translationMode) cancelPendingTranslation()
            preview = null
            statusMessage = null
            redraw()
        })
        toolbar.addView(key("⌨", KeyStyle.SPECIAL, 0.7f) {
            switchToNextInputMethod(false)
        })

        if (translationMode) addTranslationControls(root)
        addLetterRows(root)
        return root
    }

    private fun addTranslationControls(root: LinearLayout) {
        val targets = row(root, height = 40)
        targets.addView(key("English", if (target == TranslationTarget.ENGLISH) KeyStyle.ACCENT else KeyStyle.SPECIAL, 1f) {
            if (target != TranslationTarget.ENGLISH) cancelPendingTranslation()
            target = TranslationTarget.ENGLISH
            preview = null
            statusMessage = null
            redraw()
        })
        targets.addView(key("Русский", if (target == TranslationTarget.RUSSIAN) KeyStyle.ACCENT else KeyStyle.SPECIAL, 1f) {
            if (target != TranslationTarget.RUSSIAN) cancelPendingTranslation()
            target = TranslationTarget.RUSSIAN
            preview = null
            statusMessage = null
            redraw()
        })

        root.addView(TextView(this).apply {
            text = preview?.translatedText ?: statusMessage ?: "Nhấn Dịch để xem bản nháp tự nhiên hơn."
            textSize = 14f
            setTextColor(Color.rgb(39, 51, 63))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBackground(Color.WHITE, KeyStyle.NORMAL)
        }, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(2)
            bottomMargin = dp(2)
        })

        val actions = row(root, height = 40)
        actions.addView(key(if (translating) "Đang dịch…" else "Dịch bản nháp", KeyStyle.ACCENT, 1f) {
            if (!translating) translateCurrentDraft()
        }.apply { isEnabled = !translating })
        actions.addView(key("Dùng", KeyStyle.SPECIAL, 0.65f) {
            applyPreview()
        }.apply { isEnabled = preview != null && !translating })
        actions.addView(key("Hủy", KeyStyle.SPECIAL, 0.65f) {
            cancelPendingTranslation()
            preview = null
            statusMessage = "Đã bỏ bản xem trước; nội dung trong ô chat vẫn giữ nguyên."
            redraw()
        }.apply { isEnabled = preview != null || translating })
    }

    private fun addLetterRows(root: LinearLayout) {
        val rows = if (symbols) {
            listOf("1234567890", "@#$%&-*+(", "[]{}<>?!")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }

        rows.forEachIndexed { index, letters ->
            val keyRow = row(root, height = 47)
            if (index == 2) {
                keyRow.addView(key("⇧", KeyStyle.SPECIAL, 1.25f) {
                    shifted = !shifted
                    redraw()
                })
            }
            letters.forEach { letter ->
                val rendered = if (shifted) letter.uppercaseChar().toString() else letter.toString()
                keyRow.addView(key(rendered, KeyStyle.NORMAL, 1f) { insertCharacter(rendered.single()) })
            }
            if (index == 2) {
                keyRow.addView(key("⌫", KeyStyle.SPECIAL, 1.25f) { deleteBackward() })
            }
        }

        val bottom = row(root, height = 48)
        bottom.addView(key(if (symbols) "ABC" else "?123", KeyStyle.SPECIAL, 1.0f) {
            symbols = !symbols
            redraw()
        })
        bottom.addView(key(",", KeyStyle.SPECIAL, 0.75f) { insertCharacter(',') })
        bottom.addView(key("Tiếng Việt / English", KeyStyle.NORMAL, 3.5f) { insertText(" ") })
        bottom.addView(key(".", KeyStyle.SPECIAL, 0.75f) { insertCharacter('.') })
        bottom.addView(key("↵", KeyStyle.ACCENT, 1f) { insertText("\n") })
    }

    private fun translateCurrentDraft() {
        val editor = currentInputEditorInfo
        val inputConnection = currentInputConnection
        if (editor == null || inputConnection == null) {
            statusMessage = "Chưa có ô nhập văn bản đang hoạt động."
            redraw()
            return
        }
        if (!TranslationPolicy.mayTranslate(editor.inputType)) {
            statusMessage = "Dịch chỉ bật trong ô văn bản thông thường; trường mật khẩu/số được bảo vệ."
            redraw()
            return
        }

        val extracted = extractFullDraft() ?: run {
            statusMessage = "Ứng dụng chat không cung cấp toàn bộ bản nháp cho bàn phím. Nội dung chưa bị thay đổi."
            redraw()
            return
        }
        val source = extracted.text?.toString().orEmpty()
        if (source.isBlank()) {
            statusMessage = "Hãy nhập tin nhắn trước khi dịch."
            redraw()
            return
        }
        if (source.length > TranslationPolicy.MAX_DRAFT_LENGTH) {
            statusMessage = "Bản nháp quá dài; giới hạn hiện tại là ${TranslationPolicy.MAX_DRAFT_LENGTH} ký tự."
            redraw()
            return
        }

        preview = null
        statusMessage = "Model khởi tạo lần đầu có thể mất vài giây…"
        translating = true
        val requestId = ++translationRequestId
        val requestedTarget = target
        redraw()
        translator.translate(source, requestedTarget) { result ->
            if (serviceDestroyed || requestId != translationRequestId) return@translate
            translating = false
            val latestDraft = extractFullDraft()?.text?.toString()
            if (latestDraft != source) {
                statusMessage = "Bản nháp đã thay đổi trong lúc dịch. Hãy bấm Dịch lại."
                preview = null
            } else {
                result.onSuccess { translated ->
                    if (TranslationPolicy.canApply(latestDraft.orEmpty(), source, translated)) {
                        preview = TranslationPreview(source, translated)
                        statusMessage = "Xem lại rồi bấm Dùng để thay nội dung trong ô chat."
                    } else {
                        statusMessage = "Không nhận được bản dịch hợp lệ. Nội dung gốc vẫn được giữ."
                        preview = null
                    }
                }.onFailure {
                    statusMessage = "Chưa dịch được. Hãy kiểm tra mô hình đã tải xong và thử lại."
                    preview = null
                }
            }
            redraw()
        }
    }

    private fun applyPreview() {
        val selectedPreview = preview ?: return
        if (currentInputEditorInfo?.inputType?.let(TranslationPolicy::mayTranslate) != true) {
            preview = null
            statusMessage = "Không thể áp dụng trong trường nhập này; nội dung vẫn được giữ nguyên."
            redraw()
            return
        }
        val inputConnection = currentInputConnection ?: return
        val currentText = extractFullDraft()?.text?.toString()
        if (!TranslationPolicy.canApply(currentText.orEmpty(), selectedPreview.source, selectedPreview.translatedText)) {
            statusMessage = "Bản nháp đã đổi sau khi dịch. Nội dung được giữ nguyên; hãy dịch lại."
            preview = null
            redraw()
            return
        }

        inputConnection.beginBatchEdit()
        val applied = try {
            inputConnection.setSelection(0, selectedPreview.source.length) &&
                inputConnection.commitText(selectedPreview.translatedText, 1)
        } finally {
            inputConnection.endBatchEdit()
        }
        if (applied) {
            preview = null
            statusMessage = "Đã thay bản nháp. Bạn tự gửi tin trong ứng dụng chat."
        } else {
            statusMessage = "Không thể cập nhật ô chat; nội dung chưa được gửi."
        }
        redraw()
    }

    private fun extractFullDraft(): ExtractedText? {
        if (currentInputEditorInfo?.inputType?.let(TranslationPolicy::mayTranslate) != true) return null
        val request = ExtractedTextRequest().apply {
            hintMaxChars = TranslationPolicy.MAX_DRAFT_LENGTH + 1
            hintMaxLines = 20
        }
        val extracted = currentInputConnection?.getExtractedText(request, 0) ?: return null
        if (extracted.startOffset != 0 || extracted.partialStartOffset != -1 || extracted.partialEndOffset != -1) {
            return null
        }
        return extracted
    }

    private fun insertCharacter(character: Char) {
        val connection = currentInputConnection ?: return
        clearTranslationPreview()
        val rendered = if (shifted) character.uppercaseChar() else character
        shifted = false

        val editor = currentInputEditorInfo
        val plainTextField = editor == null || TranslationPolicy.mayTranslate(editor.inputType)
        val selectedText = if (vietnameseInput && plainTextField) connection.getSelectedText(0) else null
        val isPlainVietnameseText = vietnameseInput && plainTextField && selectedText.isNullOrEmpty()
        if (!isPlainVietnameseText || !rendered.isLetter()) {
            connection.commitText(rendered.toString(), 1)
            redraw()
            return
        }

        val beforeCursor = connection.getTextBeforeCursor(128, 0)?.toString()
        if (beforeCursor == null) {
            connection.commitText(rendered.toString(), 1)
            redraw()
            return
        }
        val word = beforeCursor.takeLastWhile(Char::isLetter)
        val replacement = TelexComposer.append(word, rendered)
        connection.beginBatchEdit()
        try {
            if (word.isNotEmpty()) connection.deleteSurroundingText(word.length, 0)
            connection.commitText(replacement, 1)
        } finally {
            connection.endBatchEdit()
        }
        redraw()
    }

    private fun insertText(text: String) {
        clearTranslationPreview()
        currentInputConnection?.commitText(text, 1)
        redraw()
    }

    private fun deleteBackward() {
        clearTranslationPreview()
        val connection = currentInputConnection ?: return
        val editor = currentInputEditorInfo
        val isPlainText = editor == null || TranslationPolicy.mayTranslate(editor.inputType)
        val selectedText = if (isPlainText) connection.getSelectedText(0) else null
        if (!selectedText.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else if (android.os.Build.VERSION.SDK_INT >= 24) {
            connection.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
        redraw()
    }

    private fun clearTranslationPreview() {
        preview = null
        if (!translating) statusMessage = null
    }

    private fun cancelPendingTranslation() {
        if (translating) {
            translationRequestId += 1
            translating = false
        }
    }

    private fun redraw() {
        if (window?.window?.decorView != null) setInputView(createKeyboard())
    }

    private fun row(parent: LinearLayout, height: Int): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(height)))
    }

    private fun key(label: String, style: KeyStyle, weight: Float, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = if (label.length > 6) 14f else 17f
        gravity = Gravity.CENTER
        setTextColor(if (style == KeyStyle.ACCENT) Color.WHITE else Color.rgb(37, 49, 61))
        background = roundedBackground(
            if (isEnabled) when (style) {
                KeyStyle.NORMAL -> Color.WHITE
                KeyStyle.SPECIAL -> Color.rgb(202, 212, 222)
                KeyStyle.ACCENT -> Color.rgb(40, 105, 157)
            } else Color.rgb(212, 218, 224),
            style,
        )
        alpha = if (isEnabled) 1f else 0.55f
        layoutParams = LinearLayout.LayoutParams(0, -1, weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { if (isEnabled) onClick() }
    }

    private fun roundedBackground(color: Int, style: KeyStyle): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(if (style == KeyStyle.NORMAL) 7 else 9).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class KeyStyle {
        NORMAL,
        SPECIAL,
        ACCENT,
    }

    private data class TranslationPreview(
        val source: String,
        val translatedText: String,
    )
}
