package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FileKaraokeStemCacheTest {
    @Test
    fun persistsAndReloadsSeparatedStems() = runBlocking {
        val root = Files.createTempDirectory("karavox-stem-cache").toFile()
        val sources = Files.createTempDirectory("karavox-stem-source").toFile()
        try {
            val instrumental = sources.resolve("instrumental.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val vocals = sources.resolve("vocals.wav").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val audio = ResolvedKaraokeAudio(
                uri = "file:///source.wav",
                cacheKey = "source-key",
                durationMs = 10_000L,
            )
            val cache = FileKaraokeStemCache(root, engineVersion = "mdx-v1")

            val cached = cache.put(
                audio = audio,
                quality = SeparationQuality.BALANCED,
                stems = AudioStemSet(
                    instrumentalUri = instrumental.toURI().toString(),
                    vocalsUri = vocals.toURI().toString(),
                    sourceFingerprint = "abc123",
                ),
            )

            assertTrue(cached.instrumentalUri.startsWith("file:"))
            assertTrue(cached.vocalsUri.startsWith("file:"))

            val restored = cache.get(audio, SeparationQuality.BALANCED)
            assertNotNull(restored)
            assertEquals("abc123", restored?.sourceFingerprint)
            assertEquals(byteArrayOf(1, 2, 3).toList(), java.io.File(java.net.URI(restored!!.instrumentalUri)).readBytes().toList())
        } finally {
            root.deleteRecursively()
            sources.deleteRecursively()
        }
    }

    @Test
    fun cacheIsInvalidatedByQualityOrEngineVersion() = runBlocking {
        val root = Files.createTempDirectory("karavox-stem-cache").toFile()
        val sources = Files.createTempDirectory("karavox-stem-source").toFile()
        try {
            val instrumental = sources.resolve("instrumental.wav").apply { writeBytes(byteArrayOf(1)) }
            val vocals = sources.resolve("vocals.wav").apply { writeBytes(byteArrayOf(2)) }
            val audio = ResolvedKaraokeAudio("file:///source.wav", "source-key")
            val cache = FileKaraokeStemCache(root, "engine-a")

            cache.put(
                audio,
                SeparationQuality.FAST,
                AudioStemSet(instrumental.toURI().toString(), vocals.toURI().toString()),
            )

            assertNotNull(cache.get(audio, SeparationQuality.FAST))
            assertNull(cache.get(audio, SeparationQuality.BALANCED))
            assertNull(FileKaraokeStemCache(root, "engine-b").get(audio, SeparationQuality.FAST))
        } finally {
            root.deleteRecursively()
            sources.deleteRecursively()
        }
    }
}
