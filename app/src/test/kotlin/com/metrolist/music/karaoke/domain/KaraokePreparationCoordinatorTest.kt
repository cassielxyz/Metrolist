package com.metrolist.music.karaoke.domain

import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.KaraokeLine
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokePreparationState
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.model.LyricsSource
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokePreparationCoordinatorTest {
    private val song = KaraokeSongRef(
        id = "song-1",
        title = "Test song",
        artist = "Test artist",
        source = KaraokeSource.LOCAL,
        durationMs = 120_000L,
        mediaUri = "content://songs/1",
    )

    @Test
    fun preparesSourceStemsAndFallsBackToSecondLyricsProvider() = runBlocking {
        val states = mutableListOf<KaraokePreparationState>()
        var requestedQuality: SeparationQuality? = null

        val coordinator = KaraokePreparationCoordinator(
            sourceResolver = object : KaraokeAudioSourceResolver {
                override suspend fun resolve(song: KaraokeSongRef) = ResolvedKaraokeAudio(
                    uri = requireNotNull(song.mediaUri),
                    cacheKey = "source-cache",
                    durationMs = song.durationMs,
                )
            },
            vocalSeparator = object : VocalSeparator {
                override suspend fun separate(
                    audio: ResolvedKaraokeAudio,
                    quality: SeparationQuality,
                    onProgress: (Float) -> Unit,
                ): AudioStemSet {
                    requestedQuality = quality
                    onProgress(0.25f)
                    onProgress(1f)
                    return AudioStemSet(
                        instrumentalUri = "file:///instrumental.wav",
                        vocalsUri = "file:///vocals.wav",
                        sourceFingerprint = "fingerprint",
                    )
                }
            },
            lyricsProviders = listOf(
                object : KaraokeLyricsProvider {
                    override suspend fun getLyrics(song: KaraokeSongRef, durationMs: Long?) = null
                },
                object : KaraokeLyricsProvider {
                    override suspend fun getLyrics(song: KaraokeSongRef, durationMs: Long?) = KaraokeLyrics(
                        source = LyricsSource.LOCAL_LRC,
                        lines = listOf(
                            KaraokeLine("Hello", 0L, 1_000L),
                        ),
                    )
                },
            ),
        )

        val result = coordinator.prepare(
            song = song,
            quality = SeparationQuality.BALANCED,
            onState = states::add,
        )

        assertTrue(result.isSuccess)
        assertEquals(SeparationQuality.BALANCED, requestedQuality)
        assertEquals("file:///instrumental.wav", result.getOrThrow().stems.instrumentalUri)
        assertEquals(LyricsSource.LOCAL_LRC, result.getOrThrow().lyrics?.source)
        assertTrue(states.first() is KaraokePreparationState.ResolvingSource)
        assertTrue(states.any { it is KaraokePreparationState.Separating && it.progress == 0.25f })
        assertTrue(states.any { it is KaraokePreparationState.LoadingLyrics })
        assertTrue(states.last() is KaraokePreparationState.Ready)
    }

    @Test
    fun reportsFailureWhenSeparationFails() = runBlocking {
        val states = mutableListOf<KaraokePreparationState>()
        val coordinator = KaraokePreparationCoordinator(
            sourceResolver = object : KaraokeAudioSourceResolver {
                override suspend fun resolve(song: KaraokeSongRef) = ResolvedKaraokeAudio(
                    uri = "file:///source.wav",
                    cacheKey = "source-cache",
                    durationMs = song.durationMs,
                )
            },
            vocalSeparator = object : VocalSeparator {
                override suspend fun separate(
                    audio: ResolvedKaraokeAudio,
                    quality: SeparationQuality,
                    onProgress: (Float) -> Unit,
                ): AudioStemSet = error("separator failed")
            },
            lyricsProviders = emptyList(),
        )

        val result = coordinator.prepare(
            song = song,
            quality = SeparationQuality.FAST,
            onState = states::add,
        )

        assertTrue(result.isFailure)
        val failed = states.last() as KaraokePreparationState.Failed
        assertEquals("separator failed", failed.message)
    }
}
