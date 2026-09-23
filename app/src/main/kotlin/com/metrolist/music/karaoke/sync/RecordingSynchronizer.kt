package com.metrolist.music.karaoke.sync

import kotlin.math.abs

/**
 * Pure alignment utilities for recorded duet tracks.
 * Live microphone audio is intentionally not synchronized over the network.
 */
object RecordingSynchronizer {
    data class Alignment(
        val automaticOffsetMs: Long,
        val manualOffsetMs: Long = 0,
        val confidence: Float = 0f,
    ) {
        val effectiveOffsetMs: Long get() = automaticOffsetMs + manualOffsetMs
    }

    /** Converts a detected sync-marker position into an offset relative to the host marker. */
    fun fromMarkers(hostMarkerMs: Long, participantMarkerMs: Long): Alignment =
        Alignment(automaticOffsetMs = hostMarkerMs - participantMarkerMs, confidence = 1f)

    /**
     * Finds the lag with the highest normalized correlation around a bounded window.
     * Intended for short mono sync-marker PCM buffers, not whole-song processing.
     */
    fun estimateFromPcm(
        reference: FloatArray,
        candidate: FloatArray,
        sampleRate: Int,
        maxDelayMs: Int = 1_500,
    ): Alignment {
        if (reference.isEmpty() || candidate.isEmpty() || sampleRate <= 0) return Alignment(0, confidence = 0f)
        val maxLag = (sampleRate * maxDelayMs / 1000).coerceAtLeast(1)
        var bestLag = 0
        var bestScore = -1f

        for (lag in -maxLag..maxLag) {
            var dot = 0.0
            var refEnergy = 0.0
            var candidateEnergy = 0.0
            var count = 0
            for (i in reference.indices) {
                val j = i + lag
                if (j !in candidate.indices) continue
                val a = reference[i].toDouble()
                val b = candidate[j].toDouble()
                dot += a * b
                refEnergy += a * a
                candidateEnergy += b * b
                count++
            }
            if (count == 0 || refEnergy == 0.0 || candidateEnergy == 0.0) continue
            val score = (dot / kotlin.math.sqrt(refEnergy * candidateEnergy)).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        val offsetMs = -(bestLag * 1000L / sampleRate)
        return Alignment(offsetMs, confidence = bestScore.coerceIn(0f, 1f))
    }

    fun withManualCorrection(alignment: Alignment, correctionMs: Long): Alignment =
        alignment.copy(manualOffsetMs = correctionMs.coerceIn(-2_000, 2_000))

    fun isMeaningful(alignment: Alignment): Boolean =
        abs(alignment.effectiveOffsetMs) >= 5 && alignment.confidence >= 0.35f
}
