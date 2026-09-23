/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.model

/** Where a karaoke song was selected from. */
enum class KaraokeSource {
    ONLINE,
    LOCAL,
}

/** Recording workflow used for the current session. */
enum class KaraokeMode {
    SOLO,
    DUET_TOGETHER,
    DUET_RECORD_LATER,
}

/** Pluggable stem-separation quality profile. */
enum class SeparationQuality {
    FAST,
    BALANCED,
    BEST,
}

enum class RecordingCodec {
    AAC,
    FLAC,
    WAV,
}

enum class RecordingQuality(
    val sampleRateHz: Int,
    val bitDepth: Int?,
    val codec: RecordingCodec,
) {
    STANDARD(48_000, null, RecordingCodec.AAC),
    HIGH(48_000, null, RecordingCodec.AAC),
    STUDIO_FLAC(48_000, 24, RecordingCodec.FLAC),
    STUDIO_WAV(48_000, 24, RecordingCodec.WAV),
}

enum class LyricsSource {
    BETTER_LYRICS,
    LRCLIB,
    LOCAL_LRC,
    LOCAL_TTML,
    MANUAL,
}

data class KaraokeSongRef(
    val id: String,
    val title: String,
    val artist: String,
    val source: KaraokeSource,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    /** Local/remote media URI when one is already known. */
    val mediaUri: String? = null,
)

data class ResolvedKaraokeAudio(
    val uri: String,
    val cacheKey: String,
    val durationMs: Long? = null,
)

data class AudioStemSet(
    val instrumentalUri: String,
    val vocalsUri: String,
    /** Stable fingerprint lets the app reuse a previous separation result. */
    val sourceFingerprint: String? = null,
)

data class KaraokeWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class KaraokeLine(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<KaraokeWord> = emptyList(),
)

data class KaraokeLyrics(
    val source: LyricsSource,
    val lines: List<KaraokeLine>,
    val globalOffsetMs: Long = 0L,
)

data class KaraokeSession(
    val id: String,
    val song: KaraokeSongRef,
    val mode: KaraokeMode,
    val sourceAudio: ResolvedKaraokeAudio,
    val stems: AudioStemSet,
    val lyrics: KaraokeLyrics?,
    val keySemitones: Int = 0,
    val tempo: Float = 1f,
)

data class DuetSyncMetadata(
    /** Monotonic timestamp recorded when the shared start marker was emitted. */
    val startTimestampNs: Long,
    /** Detected sync-marker position inside the local recording. */
    val syncMarkerPositionMs: Long? = null,
    /** Device/output latency measured or manually calibrated for this take. */
    val deviceLatencyMs: Long = 0L,
    /** Final user trim applied after automatic alignment. */
    val manualOffsetMs: Long = 0L,
)

data class RecordingTake(
    val id: String,
    val sessionId: String,
    val vocalUri: String,
    val quality: RecordingQuality,
    val durationMs: Long,
    val syncMetadata: DuetSyncMetadata? = null,
)

data class AlignmentResult(
    /** Shift applied to the partner take relative to the host take. */
    val partnerOffsetMs: Long,
    val confidence: Float,
    val method: String,
)

sealed interface KaraokePreparationState {
    object Idle : KaraokePreparationState
    object ResolvingSource : KaraokePreparationState
    data class Separating(val progress: Float) : KaraokePreparationState
    object LoadingLyrics : KaraokePreparationState
    data class Ready(val session: KaraokeSession) : KaraokePreparationState
    data class Failed(val message: String) : KaraokePreparationState
}
