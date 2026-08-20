package com.swadeshiscanner.app.network

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class ProgressRequestBody(
    private val file: File,
    private val contentType: String = "application/octet-stream",
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val length = file.length()
        val buffer = ByteArray(2048) // 2KB buffer
        val fileInputStream = FileInputStream(file)
        var uploaded: Long = 0

        // Helper to update UI on Main Thread
        val handler = Handler(Looper.getMainLooper())

        fileInputStream.use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                handler.post { onProgress(uploaded, length) }
                uploaded += read
                sink.write(buffer, 0, read)
            }
        }
    }
}