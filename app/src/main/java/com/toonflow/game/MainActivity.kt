package com.toonflow.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION = 100
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让内容延伸到系统栏后面
        WindowCompat.setDecorFitsSystemWindows(window, false)

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webView = WebView(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 监听系统栏 insets，传递给 H5（单位：px）
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val top = systemBars.top
            val bottom = systemBars.bottom
            val imeBottom = ime.bottom
            val js = "window.androidInsets = {top:$top,bottom:$bottom,ime:$imeBottom};" +
                     "window.dispatchEvent(new CustomEvent('android-insets'));"
            webView.evaluateJavascript(js, null)
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }

        webView.addJavascriptInterface(JSBridge(), "Android")
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 通知 H5 进入安卓设备模式
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('android-ready'));", null)
            }
        }

        initSpeechRecognizer()
        loadHtmlFromAssets()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    dispatchEvent("speechstart")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    dispatchEvent("speechvolume", rmsdB.toString())
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    dispatchEvent("speechend")
                }
                override fun onError(error: Int) {
                    isListening = false
                    val msg = when(error) {
                        SpeechRecognizer.ERROR_AUDIO -> "audio"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
                        SpeechRecognizer.ERROR_NETWORK -> "network"
                        SpeechRecognizer.ERROR_NO_MATCH -> "nomatch"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                        else -> "error"
                    }
                    dispatchEvent("speecherror", msg)
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    dispatchEvent("speechresult", text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    dispatchEvent("speechpartial", text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun dispatchEvent(event: String, detail: String = "") {
        val js = if (detail.isNotEmpty()) {
            "window.dispatchEvent(new CustomEvent('$event', {detail: '$detail'}));"
        } else {
            "window.dispatchEvent(new CustomEvent('$event'));"
        }
        webView.evaluateJavascript(js, null)
    }

    inner class JSBridge {
        @JavascriptInterface fun startSpeech() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
                    return@runOnUiThread
                }
                if (!isListening && speechRecognizer != null) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer?.startListening(intent)
                }
            }
        }

        @JavascriptInterface fun stopSpeech() {
            runOnUiThread { speechRecognizer?.stopListening() }
        }

        @JavascriptInterface fun cancelSpeech() {
            runOnUiThread {
                speechRecognizer?.cancel()
                isListening = false
            }
        }

        @JavascriptInterface fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface fun log(msg: String) {
            android.util.Log.d("Android", msg)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            dispatchEvent("permission-granted")
        } else {
            dispatchEvent("permission-denied")
        }
    }

    private fun loadHtmlFromAssets() {
        try {
            val stream = assets.open("dist/index.html")
            val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
            val html = reader.readText()
            reader.close()
            stream.close()
            webView.loadDataWithBaseURL("http://localhost/", html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}