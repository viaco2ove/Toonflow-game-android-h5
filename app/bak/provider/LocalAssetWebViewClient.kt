package com.toonflow.game.provider

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.InputStream

class LocalAssetWebViewClient(private val context: Context) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        url: String?
    ): WebResourceResponse? {
        if (url == null) return null

        Log.d("WebViewInterceptor", "Loading: $url")

        // 只处理 android_asset 请求
        if (!url.startsWith("file:///android_asset/")) {
            return null
        }

        // 去掉前缀
        val assetPath = url.substringAfter("file:///android_asset/")

        return try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val mimeType = getMimeType(assetPath)
            Log.d("WebViewInterceptor", "Found: $assetPath ($mimeType)")
            WebResourceResponse(mimeType, "UTF-8", inputStream)
        } catch (e: Exception) {
            Log.e("WebViewInterceptor", "Failed to load: $assetPath - ${e.message}")
            null
        }
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".html") -> "text/html"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}