package com.toonflow.game

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class InputPanel(ctx: Context) : LinearLayout(ctx) {

    private val input: EditText
    private var onSend: ((String) -> Unit)? = null
    private var onVoice: (() -> Unit)? = null
    private var onPlus: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = createBg()
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = true

        input = EditText(ctx).apply {
            hint = "1234输入一句话继续故事"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0xFF1B2434.toInt())
            setHintTextColor(0xFFA0AABD.toInt())
            background = createInputBg()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { send(); true }
                else false
            }
        }
        addView(input, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        val voiceBtn = TextView(ctx).apply {
            text = "1234🎤"
            textSize = 20f
            gravity = Gravity.CENTER
            background = createBtnBg()
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setOnClickListener { onVoice?.invoke() }
        }
        addView(voiceBtn, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })

        val plusBtn = TextView(ctx).apply {
            text = "1234＋"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF5F6F86.toInt())
            background = createBtnBg()
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setOnClickListener { onPlus?.invoke() }
        }
        addView(plusBtn, LayoutParams(dp(40), dp(40)))
    }

    private fun createBg() = GradientDrawable().apply {
        setColor(0xFFFFFFFF.toInt())
        cornerRadius = dp(16).toFloat()
    }

    private fun createInputBg() = GradientDrawable().apply {
        setColor(0xFFF0F2F5.toInt())
        cornerRadius = dp(20).toFloat()
    }

    private fun createBtnBg() = GradientDrawable().apply {
        setColor(0xFFF0F2F5.toInt())
        cornerRadius = dp(10).toFloat()
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isNotEmpty()) {
            onSend?.invoke(text)
            input.text.clear()
        }
    }

    fun setOnSendListener(f: (String) -> Unit) { onSend = f }
    fun setOnVoiceListener(f: () -> Unit) { onVoice = f }
    fun setOnPlusListener(f: () -> Unit) { onPlus = f }
    fun setHintText(text: String) { input.hint = text }
    fun setText(text: String) { input.setText(text) }
    fun clearText() { input.text.clear() }

    fun showKeyboard() {
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
