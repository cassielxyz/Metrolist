/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed interface ModelInstallState {
    data object Missing : ModelInstallState
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : ModelInstallState
    data class Ready(val file: File) : ModelInstallState
    data class Invalid(val reason: String) : ModelInstallState
}

/** Runtime model storage with resumable download, atomic finalization and SHA-256 verification. */
class KaraVoxModelManager(
    private val context: Context,
) {
    private val modelDir: File
        get() = File(context.filesDir, "karaoke/models").apply { mkdirs() }

    fun modelFile(spec: SeparatorModelSpec): File = File(modelDir, spec.fileName)

    fun partialFile(spec: SeparatorModelSpec): File = File(modelDir, "${spec.fileName}.part")

    suspend fun inspect(spec: SeparatorModelSpec): ModelInstallState = withContext(Dispatchers.IO) {
        val file = modelFile(spec)
        if (!file.exists() || file.length() <= 0L) return@withContext ModelInstallState.Missing

        val expectedSha = spec.sha256
        if (expectedSha == null) {
            return@withContext ModelInstallState.Ready(file)
        }

        val actualSha = sha256(file)
        if (actualSha.equals(expectedSha, ignoreCase = true)) {
            ModelInstallState.Ready(file)
        } else {
            ModelInstallState.Invalid("SHA-256 mismatch")
        }
    }

    suspend fun install(
        spec: SeparatorModelSpec,
        onProgress: (ModelInstallState.Downloading) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val existing = inspect(spec)
        if (existing is ModelInstallState.Ready) return@withContext existing.file

        val downloadUrl = spec.downloadUrl
            ?: error("${spec.displayName} is import-only; choose a compatible ONNX file manually")

        val target = modelFile(spec)
        val partial = partialFile(spec)
        var resumeFrom = partial.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        var connection = openConnection(downloadUrl, resumeFrom)

        try {
            connection.connect()

            // A server that ignores Range and responds with 200 must not be appended to an
            // existing partial file. Restart cleanly in that case.
            if (resumeFrom > 0L && connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                connection.disconnect()
                partial.delete()
                resumeFrom = 0L
                connection = openConnection(downloadUrl, resumeFrom)
                connection.connect()
            }

            check(connection.responseCode in 200..299) {
                "Model download failed with HTTP ${connection.responseCode}"
            }

            val total = contentTotal(connection, resumeFrom) ?: spec.expectedBytes
            var readTotal = resumeFrom
            onProgress(ModelInstallState.Downloading(readTotal, total))

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial, resumeFrom > 0L)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        onProgress(ModelInstallState.Downloading(readTotal, total))
                    }
                    output.flush()
                }
            }

            val expectedSize = spec.expectedBytes ?: total
            expectedSize?.let { expected ->
                // Keep a valid prefix for the next retry when a connection ends early.
                check(partial.length() >= (expected * 0.99).toLong()) {
                    "Model download ended early at ${partial.length()} of about $expected bytes; retry to resume"
                }
            }

            spec.sha256?.let { expectedSha ->
                val actualSha = sha256(partial)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    partial.delete()
                    error("Model verification failed (SHA-256 mismatch); partial download was discarded")
                }
            }

            if (target.exists()) target.delete()
            check(partial.renameTo(target)) {
                "Could not finalize downloaded model"
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    suspend fun importModel(
        spec: SeparatorModelSpec,
        source: File,
    ): File = withContext(Dispatchers.IO) {
        require(source.exists() && source.isFile) { "Model file does not exist" }
        val target = modelFile(spec)
        val partial = File(modelDir, "${spec.fileName}.import")
        partial.delete()
        source.inputStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        spec.sha256?.let { expectedSha ->
            check(sha256(partial).equals(expectedSha, ignoreCase = true)) {
                "Imported model SHA-256 does not match the expected KaraVox model"
            }
        }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not finalize imported model" }
        target
    }

    fun delete(spec: SeparatorModelSpec): Boolean {
        val finalDeleted = modelFile(spec).let { !it.exists() || it.delete() }
        val partialDeleted = partialFile(spec).let { !it.exists() || it.delete() }
        return finalDeleted && partialDeleted
    }

    private fun openConnection(url: String, resumeFrom: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KaraVox/0.1 Android")
            setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
        }

    private fun contentTotal(connection: HttpURLConnection, resumeFrom: Long): Long? {
        val contentRange = connection.getHeaderField("Content-Range")
        val rangeTotal = contentRange
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && it != "*" }
            ?.toLongOrNull()
        if (rangeTotal != null) return rangeTotal

        val contentLength = connection.contentLengthLong.takeIf { it > 0L } ?: return null
        return if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            resumeFrom + contentLength
        } else {
            contentLength
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
