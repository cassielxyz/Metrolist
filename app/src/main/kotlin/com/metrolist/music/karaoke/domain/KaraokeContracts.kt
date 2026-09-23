/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.domain

import com.metrolist.music.karaoke.model.AlignmentResult
import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.RecordingTake
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality

/** Resolves either an online Metrolist item or a local media item into playable audio. */
interface KaraokeAudioSourceResolver {
    suspend fun resolve(song: KaraokeSongRef): ResolvedKaraokeAudio
}

/**
 * Separates source audio into instrumental and vocal stems.
 * Implementations may be ONNX, native, remote (opt-in), or test doubles.
 */
interface VocalSeparator {
    suspend fun separate(
        audio: ResolvedKaraokeAudio,
        quality: SeparationQuality,
        onProgress: (Float) -> Unit = {},
    ): AudioStemSet
}

/** A single lyric source (Better Lyrics, LRCLIB, local LRC/TTML, etc.). */
interface KaraokeLyricsProvider {
    suspend fun getLyrics(
        song: KaraokeSongRef,
        durationMs: Long?,
    ): KaraokeLyrics?
}

/** Records microphone audio separately from the instrumental playback. */
interface KaraokeRecorder {
    suspend fun start(
        session: KaraokeSession,
        quality: RecordingQuality,
    ): String

    suspend fun stop(recordingId: String): RecordingTake

    suspend fun cancel(recordingId: String)
}

/** Post-recording alignment for private duet takes. */
interface DuetAlignmentEngine {
    suspend fun align(
        host: RecordingTake,
        partner: RecordingTake,
        hostSync: DuetSyncMetadata,
        partnerSync: DuetSyncMetadata,
    ): AlignmentResult
}
