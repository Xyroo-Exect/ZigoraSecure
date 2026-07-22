package com.zigora.secure

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class WebAppBridge(
    private val context: Context,
    private val webView: WebView,
    private val performanceManager: PerformanceManager,
    private val gameOptimizer: GameOptimizer
) {
    init {
        webView.addJavascriptInterface(this, "ZigoraSecure")
    }

    // ── Core shell bridge (async — tidak freeze UI) ──────────────
    @JavascriptInterface
    fun executeShellAsync(command: String, callbackId: String) {
        Thread {
            val result = runShell(command)
            // Escape untuk JS string
            val escaped = result
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t")
            webView.post {
                webView.evaluateJavascript(
                    "if(window._shCb)window._shCb('$callbackId',\"$escaped\")", null
                )
            }
        }.start()
    }

    // Sync versi untuk backward compat (hanya untuk perintah cepat)
    @JavascriptInterface
    fun executeShell(command: String): String = runShell(command)

    // ── Rooted shell runner ──────────────────────────────────────
    private fun runShell(command: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            stdout
        } catch (e: Exception) {
            try {
                // Fallback tanpa su (untuk perintah non-root)
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                out
            } catch (e2: Exception) { "" }
        }
    }

    // ── Game Library ─────────────────────────────────────────────
    @JavascriptInterface
    fun getInstalledPackages(): String {
        return try {
            val pm = context.packageManager
            val pkgs = pm.getInstalledApplications(0)
            pkgs.joinToString("\n") { it.packageName }
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) { false }
    }

    // ── Performance ───────────────────────────────────────────────
    @JavascriptInterface
    fun enablePerformanceMode(): Boolean = performanceManager.enablePerformanceMode()

    @JavascriptInterface
    fun disablePerformanceMode(): Boolean = performanceManager.disablePerformanceMode()

    @JavascriptInterface
    fun enableGamingMode(): Boolean = performanceManager.enableGamingMode()

    @JavascriptInterface
    fun disableGamingMode(): Boolean = performanceManager.disableGamingMode()

    @JavascriptInterface
    fun freeMemory(): Boolean = performanceManager.enablePerformanceMode()

    // ── Game Optimizer ────────────────────────────────────────────
    @JavascriptInterface
    fun unlockFps(): Boolean = gameOptimizer.unlockFps()

    @JavascriptInterface
    fun lockFps(fps: Int): Boolean = gameOptimizer.lockFps(fps)

    @JavascriptInterface
    fun optimizeGame(gamePackage: String): Boolean = gameOptimizer.optimizeForGame(gamePackage)

    @JavascriptInterface
    fun launchGame(gamePackage: String): Boolean = gameOptimizer.launchGame(gamePackage)

    // ── System Info ───────────────────────────────────────────────
    @JavascriptInterface
    fun getSystemInfo(): String = JSONObject(performanceManager.getSystemInfo()).toString()

    @JavascriptInterface
    fun getGameStatus(): String = JSONObject(gameOptimizer.getGameOptimizationStatus()).toString()

    @JavascriptInterface
    fun getPerformanceStatus(): String = JSONObject(mapOf(
        "performance_mode" to performanceManager.isPerformanceModeEnabled(),
        "gaming_mode" to performanceManager.isGamingModeEnabled(),
        "game_mode" to gameOptimizer.isGameModeActive()
    )).toString()

    @JavascriptInterface
    fun isRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (e: Exception) { false }
    }

    // ── UI Helpers ────────────────────────────────────────────────
    @JavascriptInterface
    fun showToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    @JavascriptInterface
    fun vibrate(ms: Int) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(android.os.VibrationEffect.createOneShot(ms.toLong(), 128))
            else @Suppress("DEPRECATION") v.vibrate(ms.toLong())
        } catch (_: Exception) {}
    }
}
