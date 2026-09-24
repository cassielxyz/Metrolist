/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.audio

import android.content.Context
import android.net.Uri
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Turns transient online URLs and content URIs into a stable local file before decoding.
 * Original user files are never modified. HTTP sources are required to use TLS.
 */
class KaraVoxAudioMaterializer(
    context: Context,
    private val maxSourceBytes: Long = 512L * 1024L * 1024L,
) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "karaoke/source").apply { mkdirs() }

    suspend fun materialize(
        audio: ResolvedKaraokeAudio,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(audio.uri)
        if (parsed.scheme.equals("file", ignoreCase = true)) {
            return@withContext requireExisting(File(requireNotNull(parsed.path)))
        }
        if (parsed.scheme.isNullOrBlank() && audio.uri.startsWith('/')) {
            return@withContext requireExisting(File(audio.uri))
        }

        val destination = File(cacheDir, "${sha256(audio.cacheKey)}.media")
        if (destination.isFile && destination.length() > 0L) {
            onProgress(1f)
            return@withContext destination
        }

        val partial = File(cacheDir, "${destination.name}.part")
        partial.delete()
        try {
            when (parsed.scheme?.lowercase()) {
                "content", "android.resource" -> {
                    val input = appContext.contentResolver.openInputStream(parsed)
                        ?: error("Unable to open selected audio")
                    input.use { source ->
                        copyToFile(
                            source = BufferedInputStream(source),
                            destination = partial,
                            expectedBytes = null,
                            onProgress = onProgress,
                        )
                    }
                }
                "https" -> downloadHttps(audio.uri, partial, onProgress)
                "http" -> error("KaraVox refuses insecure HTTP audio sources")
                else -> error("Unsupported karaoke audio URI scheme: ${parsed.scheme}")
            }

            check(partial.isFile && partial.length() > 0L) { "Resolved audio source is empty" }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Unable to finalize local karaoke source cache" }
            onProgress(1f)
            destination
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun clearTransientSources(): Boolean = cacheDir.deleteRecursively().also {
        if (it) cacheDir.mkdirs()
    }

    private suspend fun downloadHttps(
        uri: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        val connection = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KaraVox/0.1 Android")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Audio download failed with HTTP ${connection.responseCode}"
            }
            val length = connection.contentLengthLong.takeIf { it > 0L }
            length?.let { require(it <= maxSourceBytes) { "Audio source exceeds KaraVox cache limit" } }
            BufferedInputStream(connection.inputStream).use { source ->
                copyToFile(source, destination, length, onProgress)
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyToFile(
        source: BufferedInputStream,
        destination: File,
        expectedBytes: Long?,
        onProgress: (Float) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        var copied = 0L
        BufferedOutputStream(FileOutputStream(destination)).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                coroutineContext.ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                copied += read
                require(copied <= maxSourceBytes) { "Audio source exceeds KaraVox cache limit" }
                output.write(buffer, 0, read)
                if (expectedBytes != null && expectedBytes > 0L) {
                    onProgress((copied.toFloat() / expectedBytes).coerceIn(0f, 0.99f))
                }
            }
            output.flush()
        }
    }

    private fun requireExisting(file: File): File {
        require(file.isFile && file.length() > 0L) { "Local audio file is missing or empty" }
        return file
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
