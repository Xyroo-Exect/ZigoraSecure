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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        setContentView(R.layout.activity_main)

        val performanceManager = PerformanceManager(this)
        val gameOptimizer = GameOptimizer(this)

        setupWebView()
        WebAppBridge(this, webView, performanceManager, gameOptimizer)

        // Handle window insets — inject into JS so HTML can pad correctly
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Pass insets to WebView JS
            webView.evaluateJavascript(
                "window._insets={top:${bars.top},bottom:${bars.bottom},left:${bars.left},right:${bars.right}};" +
                "var r=document.documentElement.style;" +
                "r.setProperty('--inset-top','${bars.top}px');" +
                "r.setProperty('--inset-bottom','${bars.bottom}px');",
                null
            )
            view.setPadding(bars.left, 0, bars.right, 0)
            insets
        }
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
            }
            setBackgroundColor(Color.parseColor("#040001"))
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        "window._isApk=true;" +
                        // Fix hero banner path for APK (banner2.jpg not found → use gradient)
                        "document.querySelectorAll('.hero-img').forEach(function(i){" +
                        "  i.onerror=function(){this.style.display='none';};" +
                        "});",
                        null
                    )
                    // Trigger inset update
                    ViewCompat.requestApplyInsets(webView)
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}

