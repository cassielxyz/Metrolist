package com.metrolist.music.karaoke.duet

import android.content.Context
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.RecordingTake
import com.metrolist.music.karaoke.recording.SavedKaraokeRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Private take exchange. Packages contain vocal audio + timing metadata, never the instrumental. */
class KaraokeDuetPackage(context: Context) {
    private val root = File(context.applicationContext.filesDir, "karaoke/recordings/imported").apply { mkdirs() }

    suspend fun export(recording: SavedKaraokeRecording, output: OutputStream) = withContext(Dispatchers.IO) {
        val vocal = localFile(recording.take.vocalUri)
            ?: error("Only local KaraVox recordings can be packaged")
        require(vocal.isFile && vocal.length() in 45..MAX_VOCAL_BYTES) { "Recording is missing or too large" }
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("sessionId", recording.take.sessionId)
            setProperty("title", recording.title)
            setProperty("artist", recording.artist)
            setProperty("quality", recording.take.quality.name)
            setProperty("durationMs", recording.take.durationMs.toString())
            setProperty("createdAtEpochMs", recording.createdAtEpochMs.toString())
            recording.take.syncMetadata?.let { sync ->
                setProperty("localStartTimestampNs", sync.localStartTimestampNs.toString())
                sync.roomStartErrorMs?.let { setProperty("roomStartErrorMs", it.toString()) }
                sync.syncMarkerPositionMs?.let { setProperty("syncMarkerPositionMs", it.toString()) }
                setProperty("deviceLatencyMs", sync.deviceLatencyMs.toString())
                setProperty("manualOffsetMs", sync.manualOffsetMs.toString())
            }
        }
        val manifestBytes = ByteArrayOutputStream().use { buffer ->
            properties.store(buffer, "KaraVox private duet take")
            buffer.toByteArray()
        }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(VOCAL_ENTRY))
            vocal.inputStream().buffered().use { it.copyTo(zip, 64 * 1024) }
            zip.closeEntry()
        }
    }

    suspend fun import(input: InputStream): SavedKaraokeRecording = withContext(Dispatchers.IO) {
        val temporaryVocal = File(root, ".import-${UUID.randomUUID()}.wav")
        var manifestBytes: ByteArray? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val buffer = ByteArrayOutputStream()
                            copyLimited(zip, buffer, MAX_MANIFEST_BYTES)
                            manifestBytes = buffer.toByteArray()
                        }
                        VOCAL_ENTRY -> {
                            temporaryVocal.outputStream().buffered().use { output ->
                                copyLimited(zip, output, MAX_VOCAL_BYTES)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = requireNotNull(manifestBytes) { "Duet package manifest is missing" }
            require(temporaryVocal.isFile && temporaryVocal.length() > 44L) { "Duet package vocal WAV is missing" }
            val properties = Properties().apply { load(ByteArrayInputStream(manifest)) }
            require(properties.getProperty("version") == "1") { "Unsupported duet package version" }
            val sessionId = properties.getProperty("sessionId")?.takeIf { it.isNotBlank() }
                ?: error("Duet package has no session identity")
            val quality = runCatching { RecordingQuality.valueOf(properties.getProperty("quality")) }
                .getOrElse { RecordingQuality.STUDIO_WAV }
            val id = UUID.randomUUID().toString()
            val finalVocal = File(root, "$id.wav")
            check(temporaryVocal.renameTo(finalVocal)) { "Unable to finalize imported duet take" }

            val sync = properties.getProperty("localStartTimestampNs")?.toLongOrNull()?.let { startNs ->
                DuetSyncMetadata(
                    localStartTimestampNs = startNs,
                    roomStartErrorMs = properties.getProperty("roomStartErrorMs")?.toLongOrNull(),
                    syncMarkerPositionMs = properties.getProperty("syncMarkerPositionMs")?.toLongOrNull(),
                    deviceLatencyMs = properties.getProperty("deviceLatencyMs")?.toLongOrNull() ?: 0L,
                    manualOffsetMs = properties.getProperty("manualOffsetMs")?.toLongOrNull() ?: 0L,
                )
            }
            val take = RecordingTake(
                id = id,
                sessionId = sessionId,
                vocalUri = finalVocal.toURI().toString(),
                quality = quality,
                durationMs = properties.getProperty("durationMs")?.toLongOrNull() ?: 0L,
                syncMetadata = sync,
            )
            writeRepositoryManifest(
                vocal = finalVocal,
                take = take,
                title = properties.getProperty("title").orEmpty(),
                artist = properties.getProperty("artist").orEmpty(),
                createdAt = System.currentTimeMillis(),
            )
            SavedKaraokeRecording(
                take = take,
                title = properties.getProperty("title").orEmpty(),
                artist = properties.getProperty("artist").orEmpty(),
                createdAtEpochMs = System.currentTimeMillis(),
                instrumentalUri = null,
            )
        } catch (error: Throwable) {
            temporaryVocal.delete()
            throw error
        }
    }

    private fun writeRepositoryManifest(
        vocal: File,
        take: RecordingTake,
        title: String,
        artist: String,
        createdAt: Long,
    ) {
        val properties = Properties().apply {
            setProperty("version", "2")
            setProperty("id", take.id)
            setProperty("sessionId", take.sessionId)
            setProperty("vocalUri", take.vocalUri)
            setProperty("quality", take.quality.name)
            setProperty("durationMs", take.durationMs.toString())
            setProperty("title", title)
            setProperty("artist", artist)
            setProperty("createdAtEpochMs", createdAt.toString())
            take.syncMetadata?.let { sync ->
                setProperty("localStartTimestampNs", sync.localStartTimestampNs.toString())
                sync.roomStartErrorMs?.let { setProperty("roomStartErrorMs", it.toString()) }
                sync.syncMarkerPositionMs?.let { setProperty("syncMarkerPositionMs", it.toString()) }
                setProperty("deviceLatencyMs", sync.deviceLatencyMs.toString())
                setProperty("manualOffsetMs", sync.manualOffsetMs.toString())
            }
        }
        File(vocal.parentFile, "${take.id}.take.properties").outputStream().buffered().use {
            properties.store(it, "KaraVox imported private duet take")
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            copied += read
            require(copied <= maxBytes) { "Duet package entry exceeds safety limit" }
            output.write(buffer, 0, read)
        }
    }

    private fun localFile(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(java.net.URI(uri))
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val MANIFEST_ENTRY = "manifest.properties"
        const val VOCAL_ENTRY = "vocal.wav"
        const val MAX_MANIFEST_BYTES = 128L * 1024L
        const val MAX_VOCAL_BYTES = 512L * 1024L * 1024L
    }
}
