/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.domain

import com.metrolist.music.karaoke.model.KaraokeMode
import com.metrolist.music.karaoke.model.KaraokePreparationState
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.SeparationQuality

/**
 * Shared preparation pipeline used by online and local karaoke entries.
 *
 * Resolve source -> separate vocals -> resolve lyrics -> create one immutable session.
 * Keeping this coordinator free of Android/UI types makes it straightforward to test.
 */
class KaraokePreparationCoordinator(
    private val sourceResolver: KaraokeAudioSourceResolver,
    private val vocalSeparator: VocalSeparator,
    private val lyricsProviders: List<KaraokeLyricsProvider>,
) {
    suspend fun prepare(
        song: KaraokeSongRef,
        quality: SeparationQuality,
        mode: KaraokeMode = KaraokeMode.SOLO,
        onState: (KaraokePreparationState) -> Unit = {},
    ): Result<KaraokeSession> {
        return runCatching {
            onState(KaraokePreparationState.ResolvingSource)
            val source = sourceResolver.resolve(song)

            onState(KaraokePreparationState.Separating(0f))
            val stems = vocalSeparator.separate(source, quality) { progress ->
                onState(KaraokePreparationState.Separating(progress.coerceIn(0f, 1f)))
            }

            onState(KaraokePreparationState.LoadingLyrics)
            var lyrics = lyricsProviders.firstOrNull()?.getLyrics(song, source.durationMs)
            if (lyrics == null && lyricsProviders.size > 1) {
                for (index in 1 until lyricsProviders.size) {
                    lyrics = lyricsProviders[index].getLyrics(song, source.durationMs)
                    if (lyrics != null) break
                }
            }

            val session = KaraokeSession(
                id = "${song.source.name.lowercase()}:${song.id}",
                song = song,
                mode = mode,
                sourceAudio = source,
                stems = stems,
                lyrics = lyrics,
            )
            onState(KaraokePreparationState.Ready(session))
            session
        }.onFailure { error ->
            onState(
                KaraokePreparationState.Failed(
                    error.message ?: "Unable to prepare karaoke",
                ),
            )
        }
    }
}
