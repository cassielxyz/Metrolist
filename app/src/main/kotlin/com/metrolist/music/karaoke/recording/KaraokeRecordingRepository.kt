/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.recording

import android.content.Context
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.RecordingTake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.Properties

data class SavedKaraokeRecording(
    val take: RecordingTake,
    val title: String,
    val artist: String,
    val createdAtEpochMs: Long,
)

/** Local-only metadata index for solo and duet vocal takes. */
class KaraokeRecordingRepository(
    context: Context,
) {
    private val rootDir = File(context.applicationContext.filesDir, "karaoke/recordings")
        .apply { mkdirs() }

    suspend fun save(
        session: KaraokeSession,
        take: RecordingTake,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): SavedKaraokeRecording = withContext(Dispatchers.IO) {
        val vocalFile = fileFromUri(take.vocalUri)
            ?: error("KaraVox currently persists local recording files only")
        require(vocalFile.isFile && vocalFile.length() > 44L) { "Recorded vocal file is missing or empty" }

        val manifest = File(vocalFile.parentFile, "${take.id}.take.properties")
        val temporary = File(vocalFile.parentFile, "${take.id}.take.properties.tmp")
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("id", take.id)
            setProperty("sessionId", take.sessionId)
            setProperty("vocalUri", take.vocalUri)
            setProperty("quality", take.quality.name)
            setProperty("durationMs", take.durationMs.toString())
            setProperty("title", session.song.title)
            setProperty("artist", session.song.artist)
            setProperty("createdAtEpochMs", createdAtEpochMs.toString())
            take.syncMetadata?.let { sync ->
                setProperty("startTimestampNs", sync.startTimestampNs.toString())
                sync.syncMarkerPositionMs?.let {
                    setProperty("syncMarkerPositionMs", it.toString())
                }
                setProperty("deviceLatencyMs", sync.deviceLatencyMs.toString())
                setProperty("manualOffsetMs", sync.manualOffsetMs.toString())
            }
        }
        temporary.outputStream().buffered().use {
            properties.store(it, "KaraVox private recording take")
        }
        if (manifest.exists()) manifest.delete()
        check(temporary.renameTo(manifest)) { "Unable to persist recording metadata" }
        SavedKaraokeRecording(take, session.song.title, session.song.artist, createdAtEpochMs)
    }

    suspend fun list(): List<SavedKaraokeRecording> = withContext(Dispatchers.IO) {
        if (!rootDir.isDirectory) return@withContext emptyList()
        rootDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".take.properties") }
            .mapNotNull(::readManifest)
            .sortedByDescending { it.createdAtEpochMs }
            .toList()
    }

    suspend fun delete(recording: SavedKaraokeRecording): Boolean = withContext(Dispatchers.IO) {
        val vocal = fileFromUri(recording.take.vocalUri)
        val manifest = vocal?.parentFile?.resolve("${recording.take.id}.take.properties")
        val vocalDeleted = vocal == null || !vocal.exists() || vocal.delete()
        val manifestDeleted = manifest == null || !manifest.exists() || manifest.delete()
        vocalDeleted && manifestDeleted
    }

    private fun readManifest(file: File): SavedKaraokeRecording? = runCatching {
        val properties = Properties().apply {
            file.inputStream().buffered().use(::load)
        }
        if (properties.getProperty("version") != "1") return@runCatching null
        val vocalUri = properties.getProperty("vocalUri") ?: return@runCatching null
        val vocalFile = fileFromUri(vocalUri) ?: return@runCatching null
        if (!vocalFile.isFile || vocalFile.length() <= 44L) return@runCatching null

        val sync = properties.getProperty("startTimestampNs")?.toLongOrNull()?.let { startNs ->
            DuetSyncMetadata(
                startTimestampNs = startNs,
                syncMarkerPositionMs = properties.getProperty("syncMarkerPositionMs")?.toLongOrNull(),
                deviceLatencyMs = properties.getProperty("deviceLatencyMs")?.toLongOrNull() ?: 0L,
                manualOffsetMs = properties.getProperty("manualOffsetMs")?.toLongOrNull() ?: 0L,
            )
        }
        val take = RecordingTake(
            id = properties.getProperty("id") ?: return@runCatching null,
            sessionId = properties.getProperty("sessionId") ?: return@runCatching null,
            vocalUri = vocalUri,
            quality = RecordingQuality.valueOf(properties.getProperty("quality")),
            durationMs = properties.getProperty("durationMs")?.toLongOrNull() ?: 0L,
            syncMetadata = sync,
        )
        SavedKaraokeRecording(
            take = take,
            title = properties.getProperty("title").orEmpty(),
            artist = properties.getProperty("artist").orEmpty(),
            createdAtEpochMs = properties.getProperty("createdAtEpochMs")?.toLongOrNull() ?: 0L,
        )
    }.getOrNull()

    private fun fileFromUri(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()
}
