package com.example.mimo_optical_receiver.profiler

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier {
    LOW_TIER,
    BALANCED_TIER,
    HIGH_TIER
}

data class DeviceProfile(
    val tier: DeviceTier,
    val totalRamGb: Double,
    val targetWidth: Int,
    val targetHeight: Int
)

object DeviceProfiler {
    fun getProfile(context: Context): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        return when {
            totalRamGb <= 4.0 -> DeviceProfile(DeviceTier.LOW_TIER, totalRamGb, 1280, 720)
            totalRamGb <= 8.0 -> DeviceProfile(DeviceTier.BALANCED_TIER, totalRamGb, 1280, 720)
            else -> DeviceProfile(DeviceTier.HIGH_TIER, totalRamGb, 1280, 720)
        }
    }
}
