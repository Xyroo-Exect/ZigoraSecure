package com.zigora.secure

import android.content.Context
import android.content.Intent
import java.io.DataOutputStream

class GameOptimizer(private val context: Context) {

    private var isGameModeActive = false
    private var currentGamePackage = ""

    fun optimizeForGame(gamePackage: String): Boolean {
        currentGamePackage = gamePackage
        
        if (!isGameModeActive) {
            applyGameOptimizations()
            isGameModeActive = true
        }
        
        return true
    }

    private fun applyGameOptimizations() {
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            for (i in 0..7) {
                outputStream.writeBytes("echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor\n")
            }
            
            outputStream.writeBytes("echo max > /sys/class/kgsl/kgsl-3d0/max_gpuclk\n")
            outputStream.writeBytes("echo 0 > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel\n")
            
            outputStream.writeBytes("setprop debug.hwui.renderer opengl\n")
            outputStream.writeBytes("setprop debug.composition.type gpu\n")
            
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unlockFps(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            outputStream.writeBytes("setprop persist.sys.fps.cap 0\n")
            outputStream.writeBytes("setprop debug.fps.cap 0\n")
            outputStream.writeBytes("setprop persist.sys.max_fps 120\n")
            
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lockFps(fps: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            
            outputStream.writeBytes("setprop persist.sys.fps.cap $fps\n")
            outputStream.writeBytes("setprop persist.sys.max_fps $fps\n")
            
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchGame(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                optimizeForGame(packageName)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isGameModeActive(): Boolean = isGameModeActive

    fun getGameOptimizationStatus(): Map<String, Any> {
        return mapOf(
            "game_mode" to isGameModeActive,
            "current_game" to currentGamePackage,
            "fps_unlocked" to true
        )
    }

    fun cleanup() {
        if (isGameModeActive) {
            // Reset optimizations
        }
    }
}
