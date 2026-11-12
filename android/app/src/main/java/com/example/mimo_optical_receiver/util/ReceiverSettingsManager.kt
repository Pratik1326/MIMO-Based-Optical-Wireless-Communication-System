package com.example.mimo_optical_receiver.util

import android.content.Context
import android.content.SharedPreferences

object ReceiverSettingsManager {
    private const val PREFS_NAME = "mimo_receiver_settings"

    const val KEY_MIMO_MODE = "pref_mimo_mode"
    const val KEY_SYNC_CONFIDENCE = "pref_sync_confidence"
    const val KEY_AUTO_OPEN_FILE = "pref_auto_open_file"
    const val KEY_AUTO_EXPORT_LOGS = "pref_auto_export_logs"
    const val KEY_VERBOSE_HUD = "pref_verbose_hud"
    const val KEY_CORE_INSET = "pref_core_inset"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMimoMode(context: Context): String {
        return getPrefs(context).getString(KEY_MIMO_MODE, "AUTO") ?: "AUTO"
    }

    fun setMimoMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_MIMO_MODE, mode).apply()
    }

    fun getSyncConfidence(context: Context): Int {
        return getPrefs(context).getInt(KEY_SYNC_CONFIDENCE, 80)
    }

    fun setSyncConfidence(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_SYNC_CONFIDENCE, value).apply()
    }

    fun isAutoOpenFile(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_OPEN_FILE, false)
    }

    fun setAutoOpenFile(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_OPEN_FILE, enabled).apply()
    }

    fun isAutoExportLogs(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_EXPORT_LOGS, true)
    }

    fun setAutoExportLogs(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_EXPORT_LOGS, enabled).apply()
    }

    fun isVerboseHud(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VERBOSE_HUD, true)
    }

    fun setVerboseHud(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERBOSE_HUD, enabled).apply()
    }

    fun getCoreInset(context: Context): Int {
        return getPrefs(context).getInt(KEY_CORE_INSET, 25)
    }

    fun setCoreInset(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_CORE_INSET, value).apply()
    }
}
