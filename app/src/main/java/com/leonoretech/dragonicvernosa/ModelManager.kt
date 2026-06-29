package com.leonoretech.dragonicvernosa

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {

    private const val MODEL_FILENAME = "model.gguf"

    fun modelDir(context: Context): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILENAME)

    fun hasLocalModel(context: Context): Boolean {
        val f = modelFile(context)
        return f.exists() && f.length() > 1024 && isValidGguf(f)
    }

    /** Cek 4 byte magic header "GGUF" biar gak salah import file ngasal. */
    private fun isValidGguf(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return false
                magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Copy file GGUF yang dipilih user (via SAF) ke storage internal app. */
    fun importFromUri(context: Context, uri: Uri): Result<File> {
        return try {
            val dest = modelFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return Result.failure(Exception("Gak bisa baca file yang dipilih"))

            if (!isValidGguf(dest)) {
                dest.delete()
                return Result.failure(Exception("File ini bukan GGUF yang valid (cek 4-byte header)"))
            }
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download model dari URL langsung (misal link HuggingFace resolve/...gguf).
     * onProgress dipanggil dengan persen 0-100. Jalan di thread caller — panggil dari background thread.
     */
    fun downloadFromUrl(
        context: Context,
        urlString: String,
        onProgress: (Int) -> Unit,
        onDone: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                onError("Server balas kode ${connection.responseCode}")
                return
            }

            val totalBytes = connection.contentLengthLong
            val tempFile = File(modelDir(context), "$MODEL_FILENAME.part")
            var downloaded = 0L

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            onProgress(((downloaded * 100) / totalBytes).toInt())
                        }
                    }
                }
            }

            if (!isValidGguf(tempFile)) {
                tempFile.delete()
                onError("File yang ke-download bukan GGUF valid. Cek lagi link-nya.")
                return
            }

            val finalFile = modelFile(context)
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
            onDone(finalFile)
        } catch (e: Exception) {
            onError(e.message ?: "Download gagal")
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteModel(context: Context) {
        modelFile(context).delete()
    }
}
