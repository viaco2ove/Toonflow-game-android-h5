package com.toonflow.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowInsets
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
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val audioBuffer = ByteArrayOutputStream()

    companion object {
        private const val RECORD_AUDIO_PERMISSION = 100
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
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

        // 原生直接修改 WebView 布局，让键盘弹出时 WebView 高度自动缩小
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            // 1. 获取安全的系统窗口 Insets（合并状态栏、导航栏和刘海挖孔区域）
            val safeType = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val safeInsets = insets.getInsets(safeType)
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // 2. 获取屏幕密度，用于将物理像素转为 H5 认的 CSS 像素
            val density = resources.displayMetrics.density

            // 3. 计算 CSS 像素（保留浮点数精度，传给 H5）
            val top = safeInsets.top / density
            val bottom = safeInsets.bottom / density
            // 如果 H5 那边用到了 ime（键盘高度），顺便也算好传过去
            val ime = imeInsets.bottom / density

            // 4. 将计算好的 CSS 逻辑像素传递给 H5
            val js = "window.androidInsets = {top: $top, bottom: $bottom, ime: $ime};" +
                    "window.dispatchEvent(new CustomEvent('android-insets'));"
            webView.evaluateJavascript(js, null)

            // 注入一段侦察 JS：打印 H5 的设备像素比，并将两套单位都传给 H5
            val debugJs = """
                try {
                    var h5Info = 'H5 真实环境 -> DPR: ' + window.devicePixelRatio + 
                                 ', innerHeight: ' + window.innerHeight;
//                    window.Android.log(h5Info);
                    window.console.log(h5Info);
                  
                } catch(e) {
                    window.Android.log('注入执行错误: ' + e.message);
                }
            """.trimIndent()

            webView.evaluateJavascript(debugJs, null)

            // 5. 原生 WebView 底部边距随键盘动态抬起（这里必须用原始的物理像素 imeInsets.bottom）
            val params = webView.layoutParams as FrameLayout.LayoutParams
            params.bottomMargin = imeInsets.bottom
            webView.layoutParams = params

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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                if (request == null) return
                val resources = request.resources
                val granted = resources.filter {
                    it == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }.toTypedArray()
                if (granted.isNotEmpty()) {
                    request.grant(granted)
                } else {
                    request.deny()
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('android-ready'));", null)
            }
        }

        // 进 APP 就申请麦克风运行时权限，否则 getUserMedia 打不开音频源
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION)
        }

        loadHtmlFromAssets()
    }

    private fun dispatchEvent(event: String, detail: String = "") {
        val js = if (detail.isNotEmpty()) {
            "window.dispatchEvent(new CustomEvent('$event', {detail: '$detail'}));"
        } else {
            "window.dispatchEvent(new CustomEvent('$event'));"
        }
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    // PCM 转 WAV
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val headerSize = 44
        val totalSize = headerSize + pcmData.size
        val wav = ByteArray(totalSize)

        // RIFF header
        wav[0] = 'R'.code.toByte()
        wav[1] = 'I'.code.toByte()
        wav[2] = 'F'.code.toByte()
        wav[3] = 'F'.code.toByte()

        // Chunk size
        val chunkSize = totalSize - 8
        wav[4] = (chunkSize and 0xFF).toByte()
        wav[5] = (chunkSize shr 8 and 0xFF).toByte()
        wav[6] = (chunkSize shr 16 and 0xFF).toByte()
        wav[7] = (chunkSize shr 24 and 0xFF).toByte()

        // Format
        wav[8] = 'W'.code.toByte()
        wav[9] = 'A'.code.toByte()
        wav[10] = 'V'.code.toByte()
        wav[11] = 'E'.code.toByte()

        // Subchunk1 ID
        wav[12] = 'f'.code.toByte()
        wav[13] = 'm'.code.toByte()
        wav[14] = 't'.code.toByte()
        wav[15] = ' '.code.toByte()

        // Subchunk1 size (16 for PCM)
        wav[16] = 16
        wav[17] = 0
        wav[18] = 0
        wav[19] = 0

        // Audio format (1 for PCM)
        wav[20] = 1
        wav[21] = 0

        // Num channels
        val numChannels = 1
        wav[22] = numChannels.toByte()
        wav[23] = 0

        // Sample rate
        wav[24] = (SAMPLE_RATE and 0xFF).toByte()
        wav[25] = (SAMPLE_RATE shr 8 and 0xFF).toByte()
        wav[26] = (SAMPLE_RATE shr 16 and 0xFF).toByte()
        wav[27] = (SAMPLE_RATE shr 24 and 0xFF).toByte()

        // Byte rate = SampleRate * NumChannels * BitsPerSample/8
        val byteRate = SAMPLE_RATE * numChannels * 2 // 16-bit
        wav[28] = (byteRate and 0xFF).toByte()
        wav[29] = (byteRate shr 8 and 0xFF).toByte()
        wav[30] = (byteRate shr 16 and 0xFF).toByte()
        wav[31] = (byteRate shr 24 and 0xFF).toByte()

        // Block align = NumChannels * BitsPerSample/8
        val blockAlign = numChannels * 2
        wav[32] = blockAlign.toByte()
        wav[33] = 0

        // Bits per sample
        wav[34] = 16
        wav[35] = 0

        // Subchunk2 ID
        wav[36] = 'd'.code.toByte()
        wav[37] = 'a'.code.toByte()
        wav[38] = 't'.code.toByte()
        wav[39] = 'a'.code.toByte()

        // Subchunk2 size
        val dataSize = pcmData.size
        wav[40] = (dataSize and 0xFF).toByte()
        wav[41] = (dataSize shr 8 and 0xFF).toByte()
        wav[42] = (dataSize shr 16 and 0xFF).toByte()
        wav[43] = (dataSize shr 24 and 0xFF).toByte()

        // Copy PCM data
        System.arraycopy(pcmData, 0, wav, 44, pcmData.size)
        return wav
    }

    inner class JSBridge {
        @JavascriptInterface fun requestMicPermission() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    dispatchEvent("permission-granted")
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        RECORD_AUDIO_PERMISSION
                    )
                }
            }
        }

        @JavascriptInterface fun startSpeech() {
            runOnUiThread {
                android.util.Log.d("Android", "startSpeech called")
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("Android", "Permission not granted")
                    dispatchEvent("speecherror", "permission")
                    return@runOnUiThread
                }

                if (isRecording) {
                    android.util.Log.d("Android", "Already recording")
                    return@runOnUiThread
                }

                try {
                    val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufferSize
                    )

                    audioBuffer.reset()
                    isRecording = true
                    audioRecord?.startRecording()
                    dispatchEvent("speechstart")

                    recordingThread = Thread {
                        val buffer = ByteArray(minBufferSize)
                        try {
                            while (isRecording && audioRecord != null) {
                                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                                if (read > 0) {
                                    audioBuffer.write(buffer, 0, read)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AudioRecord", "Recording error", e)
                        }
                    }.apply { start() }

                } catch (e: Exception) {
                    android.util.Log.e("AudioRecord", "Start error", e)
                    dispatchEvent("speecherror", "start_failed")
                    isRecording = false
                }
            }
        }

        @JavascriptInterface fun stopSpeech() {
            android.util.Log.d("Android", "stopSpeech called")
            if (!isRecording || audioRecord == null) {
                android.util.Log.d("Android", "Not recording or audioRecord null")
                return
            }

            isRecording = false
            try {
                audioRecord?.stop()
                recordingThread?.join(500)
            } catch (e: Exception) {
                android.util.Log.e("AudioRecord", "Stop error", e)
            }

            val pcmData = audioBuffer.toByteArray()
            if (pcmData.size < 1024) {
                dispatchEvent("speecherror", "too_short")
                return
            }

            try {
                val wavData = pcmToWav(pcmData)
                val base64 = Base64.encodeToString(wavData, Base64.NO_WRAP)
                android.util.Log.d("Android", "Audio base64 size: ${base64.length}")
                dispatchEvent("speechresult", base64)
            } catch (e: Exception) {
                android.util.Log.e("AudioRecord", "Encode error", e)
                dispatchEvent("speecherror", "encode_failed")
            } finally {
                audioRecord?.release()
                audioRecord = null
            }
        }

        @JavascriptInterface fun cancelSpeech() {
            android.util.Log.d("Android", "cancelSpeech called")
            isRecording = false
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                recordingThread?.join(200)
            } catch (e: Exception) {}
            audioBuffer.reset()
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
            // localhost 在 Chromium 中默认就是 secure context，不需要 https
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
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {}
    }
}