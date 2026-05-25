package com.toonflow.game

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class VoiceInputPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var isCancelled = false
    private var voiceMode: VoiceMode = VoiceMode.DIALOGUE

    private var startX = 0f
    private var startY = 0f

    private lateinit var tipsText: TextView
    private lateinit var partialText: TextView
    private lateinit var voiceBtn: TextView
    private lateinit var keyboardBtn: TextView
    private lateinit var plusBtn: TextView

    private var onSendListener: ((text: String, mode: VoiceMode) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null
    private var onKeyboardListener: (() -> Unit)? = null
    private var onPlusListener: (() -> Unit)? = null

    enum class VoiceMode {
        DIALOGUE,
        ACTION
    }

    init {
        setupUI()
        setupTouchListener()
        initSpeechRecognizer()
    }

    private fun setupUI() {
        setBackgroundColor(Color.WHITE)

        // 圆角白色卡片
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
        }
        elevation = dp(8).toFloat()

        // 上方提示和识别文字区域
        val topArea = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        addView(topArea, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        // 提示文字
        tipsText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            text = "上移取消，侧移输入(动作、场景)"
        }
        topArea.addView(tipsText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        })

        // 实时识别文字
        partialText = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#1b2434"))
            gravity = Gravity.CENTER
            maxLines = 2
        }
        topArea.addView(partialText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        })

        // 底部按钮栏：[按住说话] [键盘] [+]
        val buttonBar = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        addView(buttonBar, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(BELOW, topArea.id)
        })

        // 按住说话按钮
        voiceBtn = TextView(context).apply {
            text = "按住说话"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(14), dp(24), dp(14))
            background = getDialogueBtnBg()
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        buttonBar.addView(voiceBtn, LinearLayout.LayoutParams(
            0,
            dp(52)
        ).apply {
            weight = 1f
            marginEnd = dp(8)
        })

        // 键盘切换按钮
        keyboardBtn = TextView(context).apply {
            text = "键"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(14), dp(4), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0f2f5"))
                cornerRadius = dp(10).toFloat()
            }
            setTextColor(Color.parseColor("#5f6f86"))
        }
        buttonBar.addView(keyboardBtn, LinearLayout.LayoutParams(
            dp(40),
            dp(40)
        ).apply {
            marginEnd = dp(8)
        })

        // + 按钮
        plusBtn = TextView(context).apply {
            text = "＋"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(12), dp(4), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#f0f2f5"))
                cornerRadius = dp(10).toFloat()
            }
            setTextColor(Color.parseColor("#5f6f86"))
        }
        buttonBar.addView(plusBtn, LinearLayout.LayoutParams(
            dp(40),
            dp(40)
        ))

        // 按钮点击事件
        keyboardBtn.setOnClickListener {
            onKeyboardListener?.invoke()
        }
        plusBtn.setOnClickListener {
            onPlusListener?.invoke()
        }
    }

    private fun getDialogueBtnBg(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#1b2434"))
            cornerRadius = dp(26).toFloat()
        }
    }

    private fun getActionBtnBg(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(2), Color.parseColor("#2458d8"))
        }
    }

    private fun getCancelBtnBg(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#cb4d4d"))
            cornerRadius = dp(26).toFloat()
        }
    }

    private fun setupTouchListener() {
        voiceBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startRecording()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        updateSlideState(dx, dy)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateSlideState(dx: Float, dy: Float) {
        val threshold = dp(60).toFloat()

        if (dy < -threshold) {
            if (!isCancelled) {
                isCancelled = true
                updateButtonState()
            }
            return
        }

        if (abs(dx) > threshold && dy > -threshold / 2) {
            val newMode = if (dx > 0) VoiceMode.DIALOGUE else VoiceMode.ACTION
            if (newMode != voiceMode) {
                voiceMode = newMode
                updateButtonState()
            }
        }

        isCancelled = false
        updateButtonState()
    }

    private fun updateButtonState() {
        voiceBtn.post {
            when {
                isCancelled -> {
                    voiceBtn.text = "松开取消"
                    voiceBtn.background = getCancelBtnBg()
                    voiceBtn.setTextColor(Color.WHITE)
                }
                voiceMode == VoiceMode.ACTION -> {
                    voiceBtn.text = "(动作)"
                    voiceBtn.background = getActionBtnBg()
                    voiceBtn.setTextColor(Color.parseColor("#1b2434"))
                }
                else -> {
                    voiceBtn.text = "录音中..."
                    voiceBtn.background = getDialogueBtnBg()
                    voiceBtn.setTextColor(Color.WHITE)
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isRecording = true
                    updateUIForRecording()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isRecording = false }
                override fun onError(error: Int) { isRecording = false; resetUI() }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    handleResult(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    updatePartialText(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startRecording() {
        isCancelled = false
        voiceMode = VoiceMode.DIALOGUE
        if (speechRecognizer == null) initSpeechRecognizer()
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { speechRecognizer?.startListening(intent) }
        catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopRecording() {
        speechRecognizer?.stopListening()
        isRecording = false
        if (isCancelled) onCancelListener?.invoke()
        resetUI()
    }

    private fun handleResult(text: String) {
        if (text.isNotBlank() && !isCancelled) {
            val finalText = if (voiceMode == VoiceMode.ACTION) "($text)" else text
            onSendListener?.invoke(finalText, voiceMode)
        }
        resetUI()
    }

    private fun updatePartialText(text: String) {
        post { partialText.text = text }
    }

    private fun updateUIForRecording() {
        post {
            partialText.text = ""
            voiceBtn.text = "录音中..."
            voiceBtn.background = getDialogueBtnBg()
        }
    }

    private fun resetUI() {
        post {
            isCancelled = false
            voiceMode = VoiceMode.DIALOGUE
            partialText.text = ""
            voiceBtn.text = "按住说话"
            voiceBtn.background = getDialogueBtnBg()
            voiceBtn.setTextColor(Color.WHITE)
        }
    }

    fun setOnSendListener(listener: (text: String, mode: VoiceMode) -> Unit) { onSendListener = listener }
    fun setOnCancelListener(listener: () -> Unit) { onCancelListener = listener }
    fun setOnKeyboardListener(listener: () -> Unit) { onKeyboardListener = listener }
    fun setOnPlusListener(listener: () -> Unit) { onPlusListener = listener }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        speechRecognizer?.destroy()
    }
}