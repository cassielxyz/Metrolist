/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.domain.KaraokeStemCache
import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

/**
 * File-backed cache for prepared vocal/instrumental stems.
 *
 * The cache key includes the resolved source identity, selected quality and separator engine/model
 * version so model upgrades never accidentally reuse incompatible stems.
 */
class FileKaraokeStemCache(
    private val rootDir: File,
    private val engineVersion: String,
) : KaraokeStemCache {
    override suspend fun get(
        audio: ResolvedKaraokeAudio,
        quality: SeparationQuality,
    ): AudioStemSet? = withContext(Dispatchers.IO) {
        val entry = entryDir(audio, quality)
        val manifest = File(entry, MANIFEST_FILE)
        if (!manifest.isFile) return@withContext null

        val properties = runCatching {
            Properties().apply {
                manifest.inputStream().buffered().use(::load)
            }
        }.getOrNull() ?: return@withContext null

        if (properties.getProperty("engineVersion") != engineVersion) return@withContext null
        if (properties.getProperty("sourceCacheKey") != audio.cacheKey) return@withContext null
        if (properties.getProperty("quality") != quality.name) return@withContext null

        val instrumental = File(entry, properties.getProperty("instrumentalFile") ?: return@withContext null)
        val vocals = File(entry, properties.getProperty("vocalsFile") ?: return@withContext null)
        if (!instrumental.isFile || instrumental.length() <= 0L) return@withContext null
        if (!vocals.isFile || vocals.length() <= 0L) return@withContext null

        AudioStemSet(
            instrumentalUri = instrumental.toURI().toString(),
            vocalsUri = vocals.toURI().toString(),
            sourceFingerprint = properties.getProperty("sourceFingerprint")
                ?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun put(
        audio: ResolvedKaraokeAudio,
        quality: SeparationQuality,
        stems: AudioStemSet,
    ): AudioStemSet = withContext(Dispatchers.IO) {
        val instrumentalSource = localFile(stems.instrumentalUri) ?: return@withContext stems
        val vocalsSource = localFile(stems.vocalsUri) ?: return@withContext stems
        if (!instrumentalSource.isFile || !vocalsSource.isFile) return@withContext stems

        rootDir.mkdirs()
        val finalDir = entryDir(audio, quality)
        val tempDir = File(rootDir, "${finalDir.name}.tmp-${System.nanoTime()}")
        tempDir.deleteRecursively()
        check(tempDir.mkdirs()) { "Unable to create KaraVox stem cache staging directory" }

        try {
            val instrumentalName = "instrumental.${safeExtension(instrumentalSource)}"
            val vocalsName = "vocals.${safeExtension(vocalsSource)}"
            val cachedInstrumental = File(tempDir, instrumentalName)
            val cachedVocals = File(tempDir, vocalsName)

            instrumentalSource.inputStream().buffered().use { input ->
                cachedInstrumental.outputStream().buffered().use(input::copyTo)
            }
            vocalsSource.inputStream().buffered().use { input ->
                cachedVocals.outputStream().buffered().use(input::copyTo)
            }

            check(cachedInstrumental.length() > 0L && cachedVocals.length() > 0L) {
                "Separated stem cache files are empty"
            }

            val properties = Properties().apply {
                setProperty("engineVersion", engineVersion)
                setProperty("sourceCacheKey", audio.cacheKey)
                setProperty("quality", quality.name)
                setProperty("instrumentalFile", instrumentalName)
                setProperty("vocalsFile", vocalsName)
                setProperty("sourceFingerprint", stems.sourceFingerprint.orEmpty())
            }
            File(tempDir, MANIFEST_FILE).outputStream().buffered().use {
                properties.store(it, "KaraVox prepared stem cache")
            }

            finalDir.deleteRecursively()
            check(tempDir.renameTo(finalDir)) { "Unable to finalize KaraVox prepared stem cache" }

            AudioStemSet(
                instrumentalUri = File(finalDir, instrumentalName).toURI().toString(),
                vocalsUri = File(finalDir, vocalsName).toURI().toString(),
                sourceFingerprint = stems.sourceFingerprint,
            )
        } catch (error: Throwable) {
            tempDir.deleteRecursively()
            throw error
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        rootDir.deleteRecursively()
    }

    private fun entryDir(audio: ResolvedKaraokeAudio, quality: SeparationQuality): File {
        val identity = "${audio.cacheKey}|${quality.name}|$engineVersion"
        return File(rootDir, sha256(identity))
    }

    private fun localFile(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()

    private fun safeExtension(file: File): String = file.extension
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: "wav"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MANIFEST_FILE = "manifest.properties"
    }
}
