package com.zigora.secure

import android.content.Context
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class PerformanceManager(private val context: Context) {

    private var isPerformanceModeEnabled = false
    private var isGamingModeEnabled = false

    fun enablePerformanceMode(): Boolean {
        if (isPerformanceModeEnabled) return true
        try {
            setCpuGovernor("performance")
            setCpuFrequency("max")
            disableThermalThrottling()
            setIOScheduler("noop")
            setGpuFrequency("max")
            isPerformanceModeEnabled = true
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun disablePerformanceMode(): Boolean {
        if (!isPerformanceModeEnabled) return true
        try {
            setCpuGovernor("interactive")
            setCpuFrequency("balanced")
            enableThermalThrottling()
            setIOScheduler("cfq")
            setGpuFrequency("balanced")
            isPerformanceModeEnabled = false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun enableGamingMode(): Boolean {
        if (isGamingModeEnabled) return true
        try {
            enablePerformanceMode()
            freeMemory()
            isGamingModeEnabled = true
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun disableGamingMode(): Boolean {
        if (!isGamingModeEnabled) return true
        try {
            disablePerformanceMode()
            isGamingModeEnabled = false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun setCpuGovernor(governor: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            for (i in 0..7) {
                outputStream.writeBytes("echo $governor > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setCpuFrequency(mode: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            for (i in 0..7) {
                when (mode) {
                    "max" -> {
                        val maxFreq = getCpuMaxFreq(i)
                        if (maxFreq > 0) {
                            outputStream.writeBytes("echo $maxFreq > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq\n")
                            outputStream.writeBytes("echo $maxFreq > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq\n")
                        }
                    }
                }
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getCpuMaxFreq(cpu: Int): Int {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val freq = reader.readText().trim().toIntOrNull() ?: 0
            process.waitFor()
            freq
        } catch (e: Exception) {
            0
        }
    }

    private fun setGpuFrequency(mode: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val gpuPaths = listOf("/sys/class/kgsl/kgsl-3d0/max_gpuclk", "/sys/devices/platform/kgsl/max_gpuclk")
            for (path in gpuPaths) {
                when (mode) {
                    "max" -> {
                        val maxFreq = getGpuMaxFreq()
                        if (maxFreq > 0) {
                            outputStream.writeBytes("echo $maxFreq > $path\n")
                        }
                    }
                }
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getGpuMaxFreq(): Int {
        return try {
            val process = Runtime.getRuntime().exec("cat /sys/class/kgsl/kgsl-3d0/max_gpuclk")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val freq = reader.readText().trim().toIntOrNull() ?: 0
            process.waitFor()
            freq
        } catch (e: Exception) {
            0
        }
    }

    private fun setIOScheduler(scheduler: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val blockDevices = arrayOf("mmcblk0", "sda", "sdb")
            for (device in blockDevices) {
                val path = "/sys/block/$device/queue/scheduler"
                outputStream.writeBytes("echo $scheduler > $path\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun disableThermalThrottling(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("stop thermal-engine\n")
            outputStream.writeBytes("stop thermal\n")
            outputStream.writeBytes("stop thermald\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun enableThermalThrottling(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("start thermal-engine\n")
            outputStream.writeBytes("start thermal\n")
            outputStream.writeBytes("start thermald\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun freeMemory(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("sync\n")
            outputStream.writeBytes("echo 3 > /proc/sys/vm/drop_caches\n")
            outputStream.writeBytes("echo 1 > /proc/sys/vm/compact_memory\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isPerformanceModeEnabled(): Boolean = isPerformanceModeEnabled
    fun isGamingModeEnabled(): Boolean = isGamingModeEnabled

    fun cleanup() {
        if (isPerformanceModeEnabled) disablePerformanceMode()
        if (isGamingModeEnabled) disableGamingMode()
    }

    fun getSystemInfo(): Map<String, Any> {
        return mapOf(
            "cpu_cores" to Runtime.getRuntime().availableProcessors(),
            "total_memory" to Runtime.getRuntime().totalMemory(),
            "free_memory" to Runtime.getRuntime().freeMemory(),
            "performance_mode" to isPerformanceModeEnabled,
            "gaming_mode" to isGamingModeEnabled,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "model" to android.os.Build.MODEL
        )
    }
}
