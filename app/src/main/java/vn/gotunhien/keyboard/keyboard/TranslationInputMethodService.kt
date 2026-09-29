package vn.gotunhien.keyboard.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import vn.gotunhien.keyboard.R
import vn.gotunhien.keyboard.input.TelexComposer
import vn.gotunhien.keyboard.translation.LocalTranslator
import vn.gotunhien.keyboard.translation.TranslationPolicy
import vn.gotunhien.keyboard.translation.TranslationTarget
import kotlin.math.abs

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
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var cancelDeleteRepeat: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        serviceDestroyed = false
        translator = LocalTranslator(this)
    }

    override fun onCreateInputView(): View = createKeyboard()

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        cancelActiveDeleteGesture()
        preview = null
        statusMessage = null
        translating = false
        translationRequestId += 1
    }

    override fun onDestroy() {
        cancelActiveDeleteGesture()
        serviceDestroyed = true
        translationRequestId += 1
        if (::translator.isInitialized) translator.close()
        super.onDestroy()
    }

    private fun createKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(7))
            setBackgroundColor(if (translationMode) Color.rgb(238, 244, 249) else Color.rgb(226, 231, 236))
        }

        if (translationMode) {
            addTranslationControls(root)
        } else {
            addStandardToolbar(root)
        }
        addLetterRows(root)
        return root
    }

    private fun addStandardToolbar(root: LinearLayout) {
        val toolbar = row(root, height = 40)
        toolbar.addView(key(if (vietnameseInput) "VI" else "EN", KeyStyle.SPECIAL, 0.8f) {
            vietnameseInput = !vietnameseInput
            statusMessage = null
            redraw()
        })
        toolbar.addView(key("Dịch", KeyStyle.ACCENT, 1.5f) {
            translationMode = true
            preview = null
            statusMessage = null
            redraw()
        })
        toolbar.addView(key("⌨", KeyStyle.SPECIAL, 0.7f) {
            switchToNextInputMethod(false)
        })
    }

    private fun addTranslationControls(root: LinearLayout) {
        val languages = FrameLayout(this)
        root.addView(languages, LinearLayout.LayoutParams(-1, dp(42)))

        val back = translationPill("←", Color.WHITE, Color.rgb(32, 32, 32), 20, 29f).apply {
            contentDescription = "Quay lại bàn phím thường"
            setOnClickListener {
                translationMode = false
                cancelPendingTranslation()
                preview = null
                statusMessage = null
                redraw()
            }
        }
        languages.addView(back, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(6)
        })

        val languagePicker = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        languagePicker.addView(translationPill("Tiếng Việt", Color.rgb(214, 228, 250), Color.rgb(23, 67, 137), 20, 18f),
            LinearLayout.LayoutParams(dp(106), dp(38)))
        languagePicker.addView(translationPill("⇄", Color.TRANSPARENT, Color.rgb(74, 82, 91), 0, 25f).apply {
            contentDescription = "Chiều dịch từ Tiếng Việt"
        }, LinearLayout.LayoutParams(dp(18), dp(38)).apply {
            marginStart = dp(18)
            marginEnd = dp(16)
        })
        val targetPill = translationPill(
            if (target == TranslationTarget.ENGLISH) "Tiếng Anh" else "Tiếng Nga",
            Color.rgb(214, 228, 250),
            Color.rgb(23, 67, 137),
            20,
            18f,
        ).apply {
            contentDescription = "Ngôn ngữ đích. Chạm để đổi giữa Tiếng Anh và Tiếng Nga"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                cancelPendingTranslation()
                target = if (target == TranslationTarget.ENGLISH) TranslationTarget.RUSSIAN else TranslationTarget.ENGLISH
                preview = null
                statusMessage = null
                redraw()
            }
        }
        languagePicker.addView(targetPill, LinearLayout.LayoutParams(dp(106), dp(38)))
        languages.addView(languagePicker, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))

        root.addView(TextView(this).apply {
            text = preview?.translatedText ?: statusMessage ?: "Nhập vào đây để dịch"
            textSize = 16f
            setTextColor(if (preview != null || statusMessage != null) Color.rgb(48, 55, 64) else Color.rgb(117, 120, 125))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(14), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(37, 93, 183))
                cornerRadius = dp(28).toFloat()
            }
            contentDescription = if (preview == null) "Trạng thái dịch" else "Bản xem trước bản dịch"
        }, LinearLayout.LayoutParams(-1, dp(38)).apply {
            topMargin = dp(14)
            bottomMargin = dp(13)
            marginStart = dp(3)
            marginEnd = dp(3)
        })

        addTranslationActions(root)
    }

    private fun addTranslationActions(root: LinearLayout) {
        val actions = FrameLayout(this)
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(36)))
        val rippleColor = ColorStateList.valueOf(Color.argb(72, 50, 105, 180))

        actions.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_keyboard_apps)
            contentDescription = "Chọn bàn phím"
            isClickable = true
            isFocusable = true
            background = null
            setOnTouchListener(::handleKeyTouchFeedback)
            setOnClickListener { switchToNextInputMethod(false) }
        }, FrameLayout.LayoutParams(dp(48), dp(36), Gravity.START or Gravity.CENTER_VERTICAL))

        val translate = TextView(this).apply {
            text = if (translating) "…" else "G文"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = if (translating) "Đang dịch bản nháp" else "Dịch bản nháp"
            isEnabled = !translating
            isClickable = !translating
            isFocusable = true
            background = RippleDrawable(
                rippleColor,
                roundedBackground(Color.rgb(0, 105, 157), 18),
                roundedBackground(Color.WHITE, 18),
            )
            setOnTouchListener(::handleKeyTouchFeedback)
            setOnClickListener { if (!translating) translateCurrentDraft() }
        }
        actions.addView(translate, FrameLayout.LayoutParams(dp(64), dp(34), Gravity.START or Gravity.CENTER_VERTICAL))

        val useTranslation = ImageView(this).apply {
            setImageResource(R.drawable.ic_clipboard)
            contentDescription = "Dùng bản dịch trong ô chat"
            isEnabled = preview != null && !translating
            isClickable = isEnabled
            isFocusable = true
            alpha = 1f
            background = null
            setOnTouchListener(::handleKeyTouchFeedback)
            setOnClickListener { applyPreview() }
        }
        actions.addView(useTranslation, FrameLayout.LayoutParams(dp(48), dp(36), Gravity.START or Gravity.CENTER_VERTICAL))

        val microphone = ImageView(this).apply {
            setImageResource(R.drawable.ic_microphone)
            contentDescription = "Nhập bằng giọng nói chưa khả dụng"
            isEnabled = false
            alpha = 1f
            background = roundedBackground(Color.WHITE, 18)
        }
        actions.addView(microphone, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.START or Gravity.CENTER_VERTICAL))

        actions.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            translate.translationX = width * 0.35f - translate.width / 2f
            useTranslation.translationX = width * 0.65f - useTranslation.width / 2f
            microphone.translationX = width * 0.94f - microphone.width / 2f
        }
    }

    private fun translationPill(
        label: String,
        backgroundColor: Int,
        foregroundColor: Int,
        cornerRadius: Int,
        textSize: Float,
    ): TextView = TextView(this).apply {
        text = label
        this.textSize = textSize
        gravity = Gravity.CENTER
        setTextColor(foregroundColor)
        background = roundedBackground(backgroundColor, cornerRadius)
    }

    private fun addLetterRows(root: LinearLayout) {
        val rows = if (symbols) {
            listOf("1234567890", "@#$%&-*+(", "[]{}<>?!")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }

        rows.forEachIndexed { index, letters ->
            val keyRow = row(root, height = 50, topMarginDp = if (translationMode && index == 0) 12 else 0)
            if (index == 2) {
                keyRow.addView(key("⇧", KeyStyle.SPECIAL, 1.25f) {
                    shifted = !shifted
                    redraw()
                })
            }
            letters.forEach { letter ->
                val rendered = if (shifted) letter.uppercaseChar().toString() else letter.toString()
                keyRow.addView(key(rendered, KeyStyle.NORMAL, 1f) { insertCharacter(rendered.single()) }.apply {
                    if (!symbols && vietnameseInput) {
                        setOnLongClickListener {
                            showAlternateCharacterPopup(rendered.single(), this)
                            true
                        }
                    }
                })
            }
            if (index == 2) {
                keyRow.addView(key("⌫", KeyStyle.SPECIAL, 1.25f) { deleteBackward() }.also(::attachDeleteGestures))
            }
        }

        val bottom = row(root, height = 50)
        bottom.addView(key(if (symbols) "ABC" else "?123", KeyStyle.SPECIAL, 1.0f) {
            symbols = !symbols
            redraw()
        })
        if (translationMode) {
            bottom.addView(key("🌐", KeyStyle.SPECIAL, 0.9f) {
                vietnameseInput = !vietnameseInput
                redraw()
            }.apply { contentDescription = "Đổi ngôn ngữ gõ" })
            bottom.addView(spacebarKey(if (vietnameseInput) "Tiếng Việt" else "English", 4.2f))
            bottom.addView(key("✓", KeyStyle.ACCENT, 1f) { insertText("\n") }.apply { contentDescription = "Xuống dòng" })
        } else {
            bottom.addView(key(",", KeyStyle.SPECIAL, 0.75f) { insertCharacter(',') })
            bottom.addView(spacebarKey(if (vietnameseInput) "Tiếng Việt" else "English", 3.5f))
            bottom.addView(key(".", KeyStyle.SPECIAL, 0.75f) { insertCharacter('.') })
            bottom.addView(key("↵", KeyStyle.ACCENT, 1f) { insertText("\n") })
        }
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
        val shouldRedraw = preview != null || (!translating && statusMessage != null)
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
        if (shouldRedraw) redraw()
    }

    private fun deleteWordBackward() {
        val connection = currentInputConnection ?: return
        val editor = currentInputEditorInfo
        if (editor?.inputType?.let(TranslationPolicy::mayTranslate) != true) return

        val shouldRedraw = preview != null || (!translating && statusMessage != null)
        clearTranslationPreview()
        val selectedText = connection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            val beforeCursor = connection.getTextBeforeCursor(2048, 0)?.toString() ?: return
            val codePoints = KeyboardGesturePolicy.deleteWordCodePointCount(beforeCursor)
            if (codePoints == 0) return
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                connection.deleteSurroundingTextInCodePoints(codePoints, 0)
            } else {
                val utf16Length = beforeCursor.offsetByCodePoints(beforeCursor.length, -codePoints)
                connection.deleteSurroundingText(beforeCursor.length - utf16Length, 0)
            }
        }
        if (shouldRedraw) redraw()
    }

    private fun showAlternateCharacterPopup(character: Char, anchor: View) {
        val alternatives = KeyboardGesturePolicy.alternateCharacters(character)
        if (alternatives.isEmpty()) return

        lateinit var popup: PopupWindow
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alternatives.forEach { alternative ->
                addView(TextView(this@TranslationInputMethodService).apply {
                    text = alternative.toString()
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(37, 49, 61))
                    background = RippleDrawable(
                        ColorStateList.valueOf(Color.argb(72, 50, 105, 180)),
                        roundedBackground(Color.WHITE, 8),
                        roundedBackground(Color.WHITE, 8),
                    )
                    setOnClickListener {
                        clearTranslationPreview()
                        currentInputConnection?.commitText(alternative.toString(), 1)
                        shifted = false
                        popup.dismiss()
                        redraw()
                    }
                }, LinearLayout.LayoutParams(dp(40), -1))
            }
        }
        val content = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(4), dp(3), dp(4), dp(3))
            background = roundedBackground(Color.WHITE, 12)
            addView(choices, LinearLayout.LayoutParams(-2, -1))
        }
        val popupWidth = minOf(resources.displayMetrics.widthPixels - dp(16), dp(360)).coerceAtLeast(dp(120))
        popup = PopupWindow(content, popupWidth, dp(50), true).apply {
            elevation = dp(5).toFloat()
            isOutsideTouchable = true
            setBackgroundDrawable(roundedBackground(Color.WHITE, 12))
        }
        popup.showAsDropDown(anchor, 0, -dp(104))
    }

    private fun spacebarKey(label: String, weight: Float): TextView =
        key(label, KeyStyle.NORMAL, weight) { insertText(" ") }.also { view ->
            val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
            val cursorStep = dp(12).toFloat()
            var pointerId = MotionEvent.INVALID_POINTER_ID
            var downX = 0f
            var downY = 0f
            var cursorMode = false
            var sentSteps = 0

            view.setOnTouchListener { touched, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pointerId = event.getPointerId(0)
                        downX = event.x
                        downY = event.y
                        cursorMode = false
                        sentSteps = 0
                        touched.isPressed = true
                        touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = event.findPointerIndex(pointerId)
                        if (index < 0) {
                            touched.isPressed = false
                            pointerId = MotionEvent.INVALID_POINTER_ID
                        } else {
                            val deltaX = event.getX(index) - downX
                            val deltaY = event.getY(index) - downY
                            if (!cursorMode && KeyboardGesturePolicy.isHorizontalSwipe(deltaX, deltaY, touchSlop)) {
                                cursorMode = true
                            }
                            if (cursorMode && abs(deltaX) > abs(deltaY) * 1.2f) {
                                val pastSlop = if (deltaX < 0f) minOf(0f, deltaX + touchSlop) else maxOf(0f, deltaX - touchSlop)
                                val nextSteps = KeyboardGesturePolicy.cursorSteps(pastSlop, cursorStep)
                                moveCursorBy(nextSteps - sentSteps)
                                sentSteps = nextSteps
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                            if (!cursorMode) touched.performClick()
                        }
                        touched.isPressed = false
                        pointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                        touched.isPressed = false
                        pointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                true
            }
        }

    private fun moveCursorBy(steps: Int) {
        if (steps == 0) return
        val connection = currentInputConnection ?: return
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun attachDeleteGestures(view: TextView) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val wordSwipeThreshold = maxOf(dp(48).toFloat(), touchSlop * 2f)
        var pointerId = MotionEvent.INVALID_POINTER_ID
        var downX = 0f
        var downY = 0f
        var tracking = false
        var wordSwipe = false
        var repeated = false

        val repeatAction = object : Runnable {
            override fun run() {
                if (!tracking || wordSwipe) return
                repeated = true
                deleteBackward()
                gestureHandler.postDelayed(this, 65L)
            }
        }
        val stopRepeating: () -> Unit = {
            tracking = false
            gestureHandler.removeCallbacks(repeatAction)
        }
        val cancelGesture: () -> Unit = {
            stopRepeating()
            pointerId = MotionEvent.INVALID_POINTER_ID
            view.isPressed = false
        }

        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelActiveDeleteGesture()
                    pointerId = event.getPointerId(0)
                    downX = event.x
                    downY = event.y
                    tracking = true
                    wordSwipe = false
                    repeated = false
                    cancelDeleteRepeat = cancelGesture
                    touched.isPressed = true
                    touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    gestureHandler.postDelayed(repeatAction, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointerId)
                    if (index < 0) {
                        stopRepeating()
                        cancelDeleteRepeat = null
                        pointerId = MotionEvent.INVALID_POINTER_ID
                        touched.isPressed = false
                    } else {
                        val deltaX = event.getX(index) - downX
                        val deltaY = event.getY(index) - downY
                        if (KeyboardGesturePolicy.isDeleteWordSwipe(deltaX, deltaY, wordSwipeThreshold)) {
                            wordSwipe = true
                            stopRepeating()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    stopRepeating()
                    cancelDeleteRepeat = null
                    if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                        if (wordSwipe) deleteWordBackward() else if (!repeated) touched.performClick()
                    }
                    touched.isPressed = false
                    pointerId = MotionEvent.INVALID_POINTER_ID
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    stopRepeating()
                    cancelDeleteRepeat = null
                    touched.isPressed = false
                    pointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
            true
        }
    }

    private fun cancelActiveDeleteGesture() {
        cancelDeleteRepeat?.invoke()
        cancelDeleteRepeat = null
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

    private fun row(parent: LinearLayout, height: Int, topMarginDp: Int = 0): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        orientation = LinearLayout.HORIZONTAL
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(height)).apply {
            topMargin = dp(topMarginDp)
        })
    }

    private fun handleKeyTouchFeedback(touched: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        return false
    }

    private fun key(label: String, style: KeyStyle, weight: Float, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = if (label.length > 6) 14f else 17f
        gravity = Gravity.CENTER
        setTextColor(if (style == KeyStyle.ACCENT) Color.WHITE else Color.rgb(37, 49, 61))
        val fillColor = if (isEnabled) when (style) {
                KeyStyle.NORMAL -> Color.WHITE
                KeyStyle.SPECIAL -> Color.rgb(202, 212, 222)
                KeyStyle.ACCENT -> Color.rgb(40, 105, 157)
            } else Color.rgb(212, 218, 224)
        val cornerRadius = if (style == KeyStyle.NORMAL) 7 else 9
        background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(72, 50, 105, 180)),
            roundedBackground(fillColor, cornerRadius),
            roundedBackground(Color.WHITE, cornerRadius),
        )
        alpha = if (isEnabled) 1f else 0.55f
        layoutParams = LinearLayout.LayoutParams(0, -1, weight).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
        }
        setOnTouchListener { touched, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            false
        }
        setOnClickListener { if (isEnabled) onClick() }
    }

    private fun roundedBackground(color: Int, cornerRadiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(cornerRadiusDp).toFloat()
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
