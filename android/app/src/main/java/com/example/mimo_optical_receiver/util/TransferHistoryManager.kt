package com.example.mimo_optical_receiver.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TransferRecord(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val savedPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mimoMode: String = "1x1",
    val sessionId: String = "",
    val packetCount: Int = 0
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("fileName", fileName)
            put("fileSizeBytes", fileSizeBytes)
            put("savedPath", savedPath)
            put("timestamp", timestamp)
            put("mimoMode", mimoMode)
            put("sessionId", sessionId)
            put("packetCount", packetCount)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): TransferRecord {
            return TransferRecord(
                id = json.optString("id", UUID.randomUUID().toString()),
                fileName = json.optString("fileName", "unknown.bin"),
                fileSizeBytes = json.optLong("fileSizeBytes", 0L),
                savedPath = json.optString("savedPath", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                mimoMode = json.optString("mimoMode", "1x1"),
                sessionId = json.optString("sessionId", ""),
                packetCount = json.optInt("packetCount", 0)
            )
        }
    }
}

object TransferHistoryManager {
    private const val PREFS_NAME = "mimo_transfer_history"
    private const val KEY_HISTORY = "history_records"

    fun getHistory(context: Context): List<TransferRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<TransferRecord>()
            for (i in 0 until jsonArray.length()) {
                list.add(TransferRecord.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            // Most recent first
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addRecord(context: Context, record: TransferRecord) {
        val currentList = getHistory(context).toMutableList()
        // Deduplicate by same savedPath or identical file name and size
        currentList.removeAll { it.savedPath == record.savedPath || (it.fileName == record.fileName && it.fileSizeBytes == record.fileSizeBytes) }
        currentList.add(0, record)

        // Keep maximum 50 recent records
        val trimmed = if (currentList.size > 50) currentList.subList(0, 50) else currentList

        val jsonArray = JSONArray()
        for (item in trimmed) {
            jsonArray.put(item.toJsonObject())
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, jsonArray.toString())
            .apply()
    }

    fun deleteRecord(context: Context, id: String) {
        val currentList = getHistory(context).filterNot { it.id == id }
        val jsonArray = JSONArray()
        for (item in currentList) {
            jsonArray.put(item.toJsonObject())
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, jsonArray.toString())
            .apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    fun openFile(context: Context, record: TransferRecord) {
        val file = File(record.savedPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk: ${record.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension.lowercase(Locale.getDefault())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, record: TransferRecord) {
        val file = File(record.savedPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension.lowercase(Locale.getDefault())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${record.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
