package com.toonflow.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.util.TypedValue
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION = 100
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webView = WebView(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

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

        // 暴露JSBridge给H5
        webView.addJavascriptInterface(JSBridge(), "Android")
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 通知H5页面加载完成
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
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) {
                    isListening = false
                    val msg = when(error) {
                        SpeechRecognizer.ERROR_AUDIO -> "audio"
                        SpeechRecognizer.ERROR_CLIENT -> "client"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
                        SpeechRecognizer.ERROR_NETWORK -> "network"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "nomatch"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                        SpeechRecognizer.ERROR_SERVER -> "server"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_timeout"
                        else -> "unknown"
                    }
                    dispatchEvent("speecherror", msg)
                }
                override fun onResults(results: Bundle?) {
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
            "window.dispatchEvent(new CustomEvent('$event', {detail:'$detail'}));"
        } else {
            "window.dispatchEvent(new CustomEvent('$event'));"
        }
        webView.evaluateJavascript(js, null)
    }

    // JSBridge - 只暴露原生能力，不控制UI
    inner class JSBridge {
        @JavascriptInterface fun startSpeech() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
                    return@runOnUiThread
                }
                if (!isListening) {
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

        @JavascriptInterface fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface fun log(msg: String) {
            android.util.Log.d("Android", msg)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('permission-granted'));", null)
            } else {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('permission-denied'));", null)
            }
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