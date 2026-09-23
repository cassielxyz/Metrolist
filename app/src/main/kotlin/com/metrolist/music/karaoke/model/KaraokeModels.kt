package com.metrolist.music.karaoke.model

import java.util.UUID

enum class KaraokeMode { SOLO, DUET_TOGETHER, DUET_ASYNC }
enum class SeparationQuality { FAST, BALANCED, BEST }
enum class RecordingQuality { STANDARD, HIGH, STUDIO }
enum class SessionState { CREATED, PREPARING, READY, COUNTDOWN, RECORDING, PROCESSING, COMPLETE, FAILED }

data class KaraokeSong(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sourceUri: String,
    val isLocal: Boolean,
)

data class KaraokeStemSet(
    val instrumentalUri: String,
    val vocalsUri: String?,
    val modelId: String,
    val createdAtEpochMs: Long,
)

data class RecordingTrack(
    val participantId: String,
    val uri: String,
    val sampleRate: Int = 48_000,
    val bitDepth: Int = 24,
    val measuredOffsetMs: Long = 0,
    val manualOffsetMs: Long = 0,
) {
    val effectiveOffsetMs: Long get() = measuredOffsetMs + manualOffsetMs
}

data class KaraokeParticipant(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val ready: Boolean = false,
)

data class KaraokeSession(
    val id: String = UUID.randomUUID().toString(),
    val mode: KaraokeMode,
    val song: KaraokeSong,
    val state: SessionState = SessionState.CREATED,
    val participants: List<KaraokeParticipant> = emptyList(),
    val stems: KaraokeStemSet? = null,
    val recordings: List<RecordingTrack> = emptyList(),
    val lyricsOffsetMs: Long = 0,
    val keySemitones: Int = 0,
    val tempo: Float = 1f,
)
