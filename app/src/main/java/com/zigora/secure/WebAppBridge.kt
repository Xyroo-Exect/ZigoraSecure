package com.zigora.secure

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class WebAppBridge(
    private val context: Context,
    private val webView: WebView,
    private val performanceManager: PerformanceManager,
    private val gameOptimizer: GameOptimizer
) {
    // Simpan hasil shell di map — hindari escaping issues di evaluateJavascript
    private val shResults = ConcurrentHashMap<String, String>()

    init {
        webView.addJavascriptInterface(this, "ZigoraSecure")
    }

    // ── Shell bridge ─────────────────────────────────────────────
    // JS ambil hasil via getShResult() — tidak ada string escaping masalah
    @JavascriptInterface
    fun executeShellAsync(command: String, callbackId: String) {
        Thread {
            val result = runShell(command)
            shResults[callbackId] = result
            webView.post {
                webView.evaluateJavascript(
                    "if(window._shCb)window._shCb('$callbackId')", null
                )
            }
        }.start()
    }

    // JS panggil ini untuk ambil hasil setelah callback
    @JavascriptInterface
    fun getShResult(callbackId: String): String {
        return shResults.remove(callbackId) ?: ""
    }

    @JavascriptInterface
    fun executeShell(command: String): String = runShell(command)

    private fun runShell(command: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out
        } catch (e: Exception) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                out
            } catch (e2: Exception) { "" }
        }
    }

    // ── Battery ───────────────────────────────────────────────────
    @JavascriptInterface
    fun getBatteryInfo(): String {
        return try {
            val intent = context.registerReceiver(null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL
            val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
            JSONObject(mapOf(
                "level" to pct,
                "charging" to charging,
                "temp" to temp,
                "voltage" to voltage
            )).toString()
        } catch (e: Exception) { "{\"level\":-1}" }
    }

    // ── RAM ───────────────────────────────────────────────────────
    @JavascriptInterface
    fun getMemoryInfo(): String {
        return try {
            val mi = android.app.ActivityManager.MemoryInfo()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getMemoryInfo(mi)
            JSONObject(mapOf(
                "total" to mi.totalMem,
                "avail" to mi.availMem,
                "low" to mi.lowMemory,
                "threshold" to mi.threshold
            )).toString()
        } catch (e: Exception) { "{}" }
    }

    // ── Network ───────────────────────────────────────────────────
    @JavascriptInterface
    fun getWifiInfo(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            JSONObject(mapOf(
                "ssid" to (info.ssid?.replace("\"","") ?: ""),
                "rssi" to info.rssi,
                "linkSpeed" to info.linkSpeed,
                "ip" to android.text.format.Formatter.formatIpAddress(info.ipAddress)
            )).toString()
        } catch (e: Exception) { "{}" }
    }

    // ── Installed packages ────────────────────────────────────────
    @JavascriptInterface
    fun getInstalledPackages(): String {
        return try {
            context.packageManager
                .getInstalledApplications(0)
                .joinToString("\n") { it.packageName }
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun isAppInstalled(pkg: String): Boolean {
        return try { context.packageManager.getPackageInfo(pkg, 0); true }
        catch (e: Exception) { false }
    }

    // ── Launch ────────────────────────────────────────────────────
    @JavascriptInterface
    fun launchApp(pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun launchGame(pkg: String): Boolean = launchApp(pkg).also {
        if (it) gameOptimizer.optimizeForGame(pkg)
    }

    // ── Performance ───────────────────────────────────────────────
    @JavascriptInterface
    fun enablePerformanceMode() = performanceManager.enablePerformanceMode()
    @JavascriptInterface
    fun disablePerformanceMode() = performanceManager.disablePerformanceMode()
    @JavascriptInterface
    fun enableGamingMode() = performanceManager.enableGamingMode()
    @JavascriptInterface
    fun disableGamingMode() = performanceManager.disableGamingMode()
    @JavascriptInterface
    fun freeMemory() = performanceManager.enablePerformanceMode()
    @JavascriptInterface
    fun unlockFps() = gameOptimizer.unlockFps()
    @JavascriptInterface
    fun lockFps(fps: Int) = gameOptimizer.lockFps(fps)
    @JavascriptInterface
    fun optimizeGame(pkg: String) = gameOptimizer.optimizeForGame(pkg)

    // ── System info ───────────────────────────────────────────────
    @JavascriptInterface
    fun getSystemInfo() = JSONObject(performanceManager.getSystemInfo()).toString()
    @JavascriptInterface
    fun getGameStatus() = JSONObject(gameOptimizer.getGameOptimizationStatus()).toString()
    @JavascriptInterface
    fun getPerformanceStatus() = JSONObject(mapOf(
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

    // ── UI helpers ────────────────────────────────────────────────
    @JavascriptInterface
    fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
