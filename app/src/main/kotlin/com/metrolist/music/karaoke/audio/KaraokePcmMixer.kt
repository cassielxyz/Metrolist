/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.audio

import kotlin.math.max

/** One stereo track mixed relative to the instrumental timeline. Positive offset means later. */
data class KaraokeMixTrack(
    val audio: StereoPcmAudio,
    val gain: Float = 1f,
    val offsetMs: Long = 0L,
)

/**
 * Deterministic non-destructive mixer used for solo and duet previews/exports.
 * Inputs are never modified. Added tracks are resampled to the base track's sample rate.
 */
object KaraokePcmMixer {
    fun mix(
        instrumental: StereoPcmAudio,
        instrumentalGain: Float = 1f,
        tracks: List<KaraokeMixTrack>,
    ): StereoPcmAudio {
        require(instrumentalGain in 0f..4f) { "Instrumental gain must be between 0 and 4" }
        tracks.forEach { require(it.gain in 0f..4f) { "Track gain must be between 0 and 4" } }

        val prepared = tracks.map { track ->
            val audio = if (track.audio.sampleRateHz == instrumental.sampleRateHz) {
                track.audio
            } else {
                track.audio.resample(instrumental.sampleRateHz)
            }
            val offsetSamples = millisecondsToSamples(track.offsetMs, instrumental.sampleRateHz)
            PreparedMixTrack(audio, track.gain, offsetSamples)
        }

        var outputLength = instrumental.sampleCount
        for (track in prepared) {
            val visibleStart = max(0, track.offsetSamples)
            val trimmedPrefix = max(0, -track.offsetSamples)
            val visibleLength = (track.audio.sampleCount - trimmedPrefix).coerceAtLeast(0)
            outputLength = max(outputLength, visibleStart + visibleLength)
        }

        val left = FloatArray(outputLength)
        val right = FloatArray(outputLength)
        for (index in 0 until instrumental.sampleCount) {
            left[index] = instrumental.left[index] * instrumentalGain
            right[index] = instrumental.right[index] * instrumentalGain
        }

        for (track in prepared) {
            val sourceStart = max(0, -track.offsetSamples)
            val destinationStart = max(0, track.offsetSamples)
            val copyCount = minOf(
                track.audio.sampleCount - sourceStart,
                outputLength - destinationStart,
            ).coerceAtLeast(0)

            for (index in 0 until copyCount) {
                val sourceIndex = sourceStart + index
                val destinationIndex = destinationStart + index
                left[destinationIndex] += track.audio.left[sourceIndex] * track.gain
                right[destinationIndex] += track.audio.right[sourceIndex] * track.gain
            }
        }

        // Simple hard safety limiter for file export. Effects/normalization remain a separate,
        // replaceable stage; this only prevents integer WAV clipping.
        for (index in 0 until outputLength) {
            left[index] = left[index].coerceIn(-1f, 1f)
            right[index] = right[index].coerceIn(-1f, 1f)
        }
        return StereoPcmAudio(instrumental.sampleRateHz, left, right)
    }

    fun mixSolo(
        instrumental: StereoPcmAudio,
        vocal: StereoPcmAudio,
        vocalOffsetMs: Long = 0L,
        instrumentalGain: Float = 1f,
        vocalGain: Float = 1f,
    ): StereoPcmAudio = mix(
        instrumental = instrumental,
        instrumentalGain = instrumentalGain,
        tracks = listOf(KaraokeMixTrack(vocal, vocalGain, vocalOffsetMs)),
    )

    private fun millisecondsToSamples(milliseconds: Long, sampleRateHz: Int): Int {
        val samples = milliseconds * sampleRateHz / 1_000L
        require(samples in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Mix offset is too large"
        }
        return samples.toInt()
    }

    private data class PreparedMixTrack(
        val audio: StereoPcmAudio,
        val gain: Float,
        val offsetSamples: Int,
    )
}
