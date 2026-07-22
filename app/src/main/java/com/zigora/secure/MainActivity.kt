package com.zigora.secure

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full edge-to-edge immersive
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        }

        setContentView(R.layout.activity_main)

        val performanceManager = PerformanceManager(this)
        val gameOptimizer = GameOptimizer(this)

        setupWebView()
        WebAppBridge(this, webView, performanceManager, gameOptimizer)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                mediaPlaybackRequiresUserGesture = false
            }
            setBackgroundColor(Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Signal APK mode ke JS
                    view?.evaluateJavascript("window._isApk=true;", null)
                }
            }
            webChromeClient = WebChromeClient()
            // Load dari assets
            loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
