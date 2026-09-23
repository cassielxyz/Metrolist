package com.metrolist.music.karaoke.duet

import com.metrolist.music.karaoke.audio.Pcm16WavSampleSource
import com.metrolist.music.karaoke.domain.DuetAlignmentEngine
import com.metrolist.music.karaoke.model.AlignmentResult
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.RecordingTake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import kotlin.math.abs
import kotlin.math.sqrt

/** Metadata alignment refined with a bounded, low-rate vocal-envelope correlation pass. */
class WaveformDuetAlignmentEngine(
    private val metadataEngine: MetadataDuetAlignmentEngine = MetadataDuetAlignmentEngine(),
) : DuetAlignmentEngine {
    override suspend fun align(
        host: RecordingTake,
        partner: RecordingTake,
        hostSync: DuetSyncMetadata,
        partnerSync: DuetSyncMetadata,
    ): AlignmentResult {
        val coarse = metadataEngine.align(host, partner, hostSync, partnerSync)
        val hostFile = localFile(host.vocalUri) ?: return coarse
        val partnerFile = localFile(partner.vocalUri) ?: return coarse
        if (!hostFile.isFile || !partnerFile.isFile) return coarse

        return withContext(Dispatchers.Default) {
            runCatching {
                val hostEnvelope = envelope(hostFile)
                val partnerEnvelope = envelope(partnerFile)
                if (hostEnvelope.size < ENVELOPE_RATE * 5 || partnerEnvelope.size < ENVELOPE_RATE * 5) {
                    return@runCatching coarse
                }
                center(hostEnvelope)
                center(partnerEnvelope)

                val coarseSamples = (coarse.partnerOffsetMs * ENVELOPE_RATE / 1_000L).toInt()
                val radiusSamples = SEARCH_RADIUS_MS * ENVELOPE_RATE / 1_000
                var bestOffset = coarseSamples
                var bestScore = -1.0

                for (candidate in (coarseSamples - radiusSamples)..(coarseSamples + radiusSamples)) {
                    val hostStart = maxOf(0, candidate)
                    val partnerStart = maxOf(0, -candidate)
                    val overlap = minOf(
                        hostEnvelope.size - hostStart,
                        partnerEnvelope.size - partnerStart,
                    )
                    if (overlap < ENVELOPE_RATE * 3) continue
                    var dot = 0.0
                    var hostPower = 0.0
                    var partnerPower = 0.0
                    var index = 0
                    while (index < overlap) {
                        val h = hostEnvelope[hostStart + index].toDouble()
                        val p = partnerEnvelope[partnerStart + index].toDouble()
                        dot += h * p
                        hostPower += h * h
                        partnerPower += p * p
                        index += 1
                    }
                    val denominator = sqrt(hostPower * partnerPower)
                    if (denominator <= 1e-9) continue
                    val score = dot / denominator
                    if (score > bestScore) {
                        bestScore = score
                        bestOffset = candidate
                    }
                }

                if (bestScore < MIN_CORRELATION) return@runCatching coarse
                val offsetMs = bestOffset * 1_000L / ENVELOPE_RATE
                AlignmentResult(
                    partnerOffsetMs = offsetMs,
                    confidence = maxOf(coarse.confidence, bestScore.toFloat().coerceIn(0f, 0.98f)),
                    method = "${coarse.method}+waveform",
                )
            }.getOrElse { coarse }
        }
    }

    private fun envelope(file: File): FloatArray {
        Pcm16WavSampleSource(file).use { source ->
            val count = source.frameCountAt(ENVELOPE_RATE)
            val block = source.readResampled(0, count, ENVELOPE_RATE)
            return FloatArray(count) { index ->
                abs((block[0][index] + block[1][index]) * 0.5f)
            }
        }
    }

    private fun center(values: FloatArray) {
        if (values.isEmpty()) return
        val mean = values.sum().toDouble() / values.size
        for (index in values.indices) values[index] = (values[index] - mean).toFloat()
    }

    private fun localFile(uri: String): File? = runCatching {
        when {
            uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
            uri.startsWith("/") -> File(uri)
            else -> null
        }
    }.getOrNull()

    private companion object {
        const val ENVELOPE_RATE = 200
        const val SEARCH_RADIUS_MS = 2_500
        const val MIN_CORRELATION = 0.12
    }
}
