package com.metrolist.music.karaoke.cache

import android.content.Context
import com.metrolist.music.karaoke.lyrics.KaraokeLyricsDiskCodec
import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.KaraokeMode
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

data class SavedKaraokeSession(
    val session: KaraokeSession,
    val updatedAtEpochMs: Long,
)

/** Persistent prepared-session registry used by Library and crash/process recovery. */
class KaraokeSessionRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "karaoke/sessions").apply { mkdirs() }

    suspend fun save(
        session: KaraokeSession,
        updatedAtEpochMs: Long = System.currentTimeMillis(),
    ): SavedKaraokeSession = withContext(Dispatchers.IO) {
        val directory = directoryFor(session.id)
        val staging = File(root, "${directory.name}.tmp-${System.nanoTime()}")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create KaraVox session staging directory" }
        try {
            val properties = Properties().apply {
                setProperty("version", "1")
                setProperty("id", session.id)
                setProperty("mode", session.mode.name)
                setProperty("keySemitones", session.keySemitones.toString())
                setProperty("tempo", session.tempo.toString())
                setProperty("updatedAtEpochMs", updatedAtEpochMs.toString())
                setProperty("songId", session.song.id)
                setProperty("title", session.song.title)
                setProperty("artist", session.song.artist)
                setProperty("source", session.song.source.name)
                session.song.durationMs?.let { setProperty("songDurationMs", it.toString()) }
                session.song.artworkUrl?.let { setProperty("artworkUrl", it) }
                session.song.mediaUri?.let { setProperty("mediaUri", it) }
                setProperty("sourceAudioUri", session.sourceAudio.uri)
                setProperty("sourceCacheKey", session.sourceAudio.cacheKey)
                session.sourceAudio.durationMs?.let { setProperty("sourceDurationMs", it.toString()) }
                setProperty("instrumentalUri", session.stems.instrumentalUri)
                setProperty("vocalsUri", session.stems.vocalsUri)
                session.stems.sourceFingerprint?.let { setProperty("sourceFingerprint", it) }
                setProperty("hasLyrics", (session.lyrics != null).toString())
            }
            File(staging, MANIFEST).outputStream().buffered().use {
                properties.store(it, "KaraVox prepared karaoke session")
            }
            session.lyrics?.let { KaraokeLyricsDiskCodec.write(File(staging, LYRICS), it) }
            directory.deleteRecursively()
            check(staging.renameTo(directory)) { "Unable to finalize KaraVox session metadata" }
            SavedKaraokeSession(session, updatedAtEpochMs)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    suspend fun load(sessionId: String): KaraokeSession? = withContext(Dispatchers.IO) {
        readDirectory(directoryFor(sessionId))?.session
    }

    suspend fun list(): List<SavedKaraokeSession> = withContext(Dispatchers.IO) {
        root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.contains(".tmp-") }
            .mapNotNull(::readDirectory)
            .sortedByDescending { it.updatedAtEpochMs }
            .toList()
    }

    suspend fun delete(sessionId: String, deleteStemFiles: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val directory = directoryFor(sessionId)
        val saved = readDirectory(directory)
        if (deleteStemFiles) {
            saved?.session?.stems?.let { stems ->
                localFile(stems.instrumentalUri)?.delete()
                localFile(stems.vocalsUri)?.delete()
            }
        }
        !directory.exists() || directory.deleteRecursively()
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        root.deleteRecursively().also { if (it) root.mkdirs() }
    }

    private fun readDirectory(directory: File): SavedKaraokeSession? = runCatching {
        val manifest = File(directory, MANIFEST)
        if (!manifest.isFile) return@runCatching null
        val properties = Properties().apply { manifest.inputStream().buffered().use(::load) }
        if (properties.getProperty("version") != "1") return@runCatching null
        val id = properties.getProperty("id") ?: return@runCatching null
        if (directory != directoryFor(id)) return@runCatching null
        val instrumentalUri = properties.getProperty("instrumentalUri") ?: return@runCatching null
        val vocalsUri = properties.getProperty("vocalsUri") ?: return@runCatching null
        if (!uriExistsIfLocal(instrumentalUri) || !uriExistsIfLocal(vocalsUri)) return@runCatching null

        val song = KaraokeSongRef(
            id = properties.getProperty("songId") ?: return@runCatching null,
            title = properties.getProperty("title").orEmpty(),
            artist = properties.getProperty("artist").orEmpty(),
            source = KaraokeSource.valueOf(properties.getProperty("source")),
            durationMs = properties.getProperty("songDurationMs")?.toLongOrNull(),
            artworkUrl = properties.getProperty("artworkUrl")?.takeIf { it.isNotBlank() },
            mediaUri = properties.getProperty("mediaUri")?.takeIf { it.isNotBlank() },
        )
        val sourceAudio = ResolvedKaraokeAudio(
            uri = properties.getProperty("sourceAudioUri").orEmpty(),
            cacheKey = properties.getProperty("sourceCacheKey") ?: return@runCatching null,
            durationMs = properties.getProperty("sourceDurationMs")?.toLongOrNull(),
        )
        val stems = AudioStemSet(
            instrumentalUri = instrumentalUri,
            vocalsUri = vocalsUri,
            sourceFingerprint = properties.getProperty("sourceFingerprint")?.takeIf { it.isNotBlank() },
        )
        val lyrics = if (properties.getProperty("hasLyrics").toBoolean()) {
            KaraokeLyricsDiskCodec.read(File(directory, LYRICS))
        } else null
        val session = KaraokeSession(
            id = id,
            song = song,
            mode = KaraokeMode.valueOf(properties.getProperty("mode") ?: KaraokeMode.SOLO.name),
            sourceAudio = sourceAudio,
            stems = stems,
            lyrics = lyrics,
            keySemitones = properties.getProperty("keySemitones")?.toIntOrNull() ?: 0,
            tempo = properties.getProperty("tempo")?.toFloatOrNull() ?: 1f,
        )
        SavedKaraokeSession(
            session = session,
            updatedAtEpochMs = properties.getProperty("updatedAtEpochMs")?.toLongOrNull() ?: 0L,
        )
    }.getOrNull()

    private fun directoryFor(sessionId: String): File = File(root, sha256(sessionId))

    private fun uriExistsIfLocal(uri: String): Boolean {
        val file = localFile(uri) ?: return true
        return file.isFile && file.length() > 44L
    }

    private fun localFile(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MANIFEST = "session.properties"
        const val LYRICS = "lyrics.kvx"
    }
}
