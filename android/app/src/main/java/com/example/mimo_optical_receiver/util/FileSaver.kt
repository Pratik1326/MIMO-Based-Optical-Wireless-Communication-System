package com.example.mimo_optical_receiver.util

import android.content.Context
import android.os.Environment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

object FileSaver {

    /**
     * Computes the SHA-256 hexadecimal string of a ByteArray.
     */
    fun computeSha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 of an existing File on disk.
     */
    fun computeFileSha256(file: File): String? {
        return try {
            if (!file.exists() || !file.isFile) return null
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the payload is GZIP compressed (Magic bytes 0x1F, 0x8B).
     */
    fun isGzipCompressed(data: ByteArray): Boolean {
        return data.size >= 2 &&
                (data[0].toInt() and 0xFF == 0x1F) &&
                (data[1].toInt() and 0xFF == 0x8B)
    }

    /**
     * Transparently decompresses GZIP data in background. Returns decompressed bytes,
     * or original data if decompression fails.
     */
    fun decompressIfGzip(data: ByteArray): ByteArray {
        if (!isGzipCompressed(data)) return data
        return try {
            GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
                gzip.readBytes()
            }
        } catch (e: Exception) {
            data
        }
    }

    /**
     * Saves raw byte data to the application's external Downloads directory.
     * Features:
     * 1. Transparent decompression: If data was gzipped in transit and target is not .gz,
     *    automatically decompresses to restore original file.
     * 2. Storage Deduplication: If an existing file with identical content already exists,
     *    reuses the clean path without redundant duplicate disk writes.
     * 3. Collision protection: If same filename exists with different content, avoids overwriting.
     * 
     * @param context The Android context used to resolve the external directory.
     * @param filename The name of the file to be saved.
     * @param rawData The raw contiguous ByteArray payload to write.
     * @return The absolute path of the saved file, or null if an error occurred.
     */
    fun saveFile(context: Context, filename: String, rawData: ByteArray): String? {
        return try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && !downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Transparent decompression if gzipped and filename does not explicitly request .gz
            val isGzFile = filename.endsWith(".gz", ignoreCase = true)
            val finalData = if (!isGzFile && isGzipCompressed(rawData)) {
                decompressIfGzip(rawData)
            } else {
                rawData
            }

            val finalSha256 = computeSha256(finalData)

            // Storage Deduplication Check:
            // Check if exact file with same name and identical SHA-256 already exists
            val primaryFile = File(downloadsDir, filename)
            if (primaryFile.exists() && primaryFile.length() == finalData.size.toLong()) {
                val existingHash = computeFileSha256(primaryFile)
                if (existingHash == finalSha256) {
                    DiagnosticLogger.log("FILE_SAVER", "Deduplication: Identical file already exists on disk: ${primaryFile.name}")
                    return primaryFile.absolutePath
                }
            }

            // Also check all files in downloadsDir for byte deduplication to prevent repeated storage clutter
            downloadsDir?.listFiles()?.forEach { file ->
                if (file.isFile && file.length() == finalData.size.toLong()) {
                    if (computeFileSha256(file) == finalSha256) {
                        DiagnosticLogger.log("FILE_SAVER", "Deduplication: Exact payload already stored as: ${file.name}")
                        return file.absolutePath
                    }
                }
            }

            // Resolve target file name (handle collision if different file has same name)
            var targetFile = primaryFile
            if (targetFile.exists()) {
                val baseName = filename.substringBeforeLast(".")
                val extension = if (filename.contains(".")) ".${filename.substringAfterLast(".")}" else ""
                var counter = 1
                while (targetFile.exists()) {
                    targetFile = File(downloadsDir, "${baseName}_$counter$extension")
                    counter++
                }
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(finalData)
                fos.flush()
            }
            DiagnosticLogger.log("FILE_SAVER", "Saved ${finalData.size} bytes to ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
