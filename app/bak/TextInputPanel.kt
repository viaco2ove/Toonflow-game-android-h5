package com.toonflow.game

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView

class TextInputPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private lateinit var inputEdit: EditText
    private lateinit var voiceBtn: TextView
    private lateinit var plusBtn: TextView

    private var onSendListener: ((text: String) -> Unit)? = null
    private var onVoiceListener: (() -> Unit)? = null
    private var onPlusListener: (() -> Unit)? = null
    var onPanelReady: (() -> Unit)? = null

    init {
        setupUI()
        post { onPanelReady?.invoke() }
    }

    private fun setupUI() {
        // 圆角白色卡片背景
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(8).toFloat()

        val barLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        addView(barLayout, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        // 输入框
        inputEdit = EditText(context).apply {
            hint = "123输入一句话继续故事"
            textSize = 16f
            setTextColor(Color.parseColor("#1b2434"))
            setHintTextColor(Color.parseColor("#a0aabd"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0f2f5"))
                cornerRadius = dp(20).toFloat()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendText()
                    true
                } else false
            }
        }
        barLayout.addView(inputEdit, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            weight = 1f
            marginEnd = dp(8)
        })

        // 语音切换按钮（麦克风图标）
        voiceBtn = TextView(context).apply {
            text = "🎤123"
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0f2f5"))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { onVoiceListener?.invoke() }
        }
        barLayout.addView(voiceBtn, LinearLayout.LayoutParams(
            dp(40),
            dp(40)
        ).apply {
            marginEnd = dp(8)
        })

        // + 按钮
        plusBtn = TextView(context).apply {
            text = "＋123"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5f6f86"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0f2f5"))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { onPlusListener?.invoke() }
        }
        barLayout.addView(plusBtn, LinearLayout.LayoutParams(
            dp(40),
            dp(40)
        ))
    }

    private fun sendText() {
        val text = inputEdit.text.toString().trim()
        if (text.isNotEmpty()) {
            onSendListener?.invoke(text)
            inputEdit.setText("")
        }
    }

    fun setOnSendListener(listener: (text: String) -> Unit) { onSendListener = listener }
    fun setOnVoiceListener(listener: () -> Unit) { onVoiceListener = listener }
    fun setOnPlusListener(listener: () -> Unit) { onPlusListener = listener }

    fun setHint(hint: String) { inputEdit.hint = hint }
    fun setText(text: String) { inputEdit.setText(text) }
    fun getText(): String = inputEdit.text.toString()

    fun showKeyboard() {
        inputEdit.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(inputEdit, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputEdit.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }
}