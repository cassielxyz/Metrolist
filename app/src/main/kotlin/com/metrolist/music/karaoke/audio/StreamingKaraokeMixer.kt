package com.metrolist.music.karaoke.audio

import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

data class KaraokeFileMixTrack(
    val file: File,
    val gain: Float = 1f,
    val offsetMs: Long = 0L,
)

/** Bounded-memory WAV mixer used for solo/duet exports. */
object StreamingKaraokeMixer {
    fun mix(
        instrumentalFile: File,
        vocalTracks: List<KaraokeFileMixTrack>,
        outputFile: File,
        instrumentalGain: Float = 0.88f,
        chunkFrames: Int = 16_384,
        onProgress: (Float) -> Unit = {},
    ): File {
        require(chunkFrames > 0)
        val instrumental = Pcm16WavSampleSource(instrumentalFile)
        val opened = mutableListOf<Closeable>(instrumental)
        try {
            val sampleRate = instrumental.sampleRateHz
            val tracks = vocalTracks.map { track ->
                val source = Pcm16WavSampleSource(track.file)
                opened += source
                OpenTrack(
                    source = source,
                    gain = track.gain.coerceIn(0f, 4f),
                    offsetFrames = (track.offsetMs * sampleRate / 1_000.0).roundToInt(),
                    frameCount = source.frameCountAt(sampleRate),
                )
            }
            val instrumentalFrames = instrumental.frameCountAt(sampleRate)
            val outputFrames = maxOf(
                instrumentalFrames,
                tracks.maxOfOrNull { (it.offsetFrames + it.frameCount).coerceAtLeast(0) } ?: 0,
            )
            require(outputFrames > 0) { "Nothing to mix" }
            outputFile.parentFile?.mkdirs()
            outputFile.delete()

            Pcm16WavStreamWriter(outputFile, sampleRate).use { writer ->
                var outputStart = 0
                while (outputStart < outputFrames) {
                    val count = minOf(chunkFrames, outputFrames - outputStart)
                    val left = FloatArray(count)
                    val right = FloatArray(count)

                    if (outputStart < instrumentalFrames) {
                        val instrumentalCount = minOf(count, instrumentalFrames - outputStart)
                        val block = instrumental.readResampled(outputStart, instrumentalCount, sampleRate)
                        repeat(instrumentalCount) { index ->
                            left[index] += block[0][index] * instrumentalGain
                            right[index] += block[1][index] * instrumentalGain
                        }
                    }

                    val outputEnd = outputStart + count
                    tracks.forEach { track ->
                        val trackStartOnTimeline = track.offsetFrames
                        val trackEndOnTimeline = track.offsetFrames + track.frameCount
                        val overlapStart = maxOf(outputStart, trackStartOnTimeline, 0)
                        val overlapEnd = minOf(outputEnd, trackEndOnTimeline)
                        if (overlapEnd > overlapStart) {
                            val sourceStart = overlapStart - trackStartOnTimeline
                            val blockCount = overlapEnd - overlapStart
                            val block = track.source.readResampled(sourceStart, blockCount, sampleRate)
                            val destination = overlapStart - outputStart
                            repeat(blockCount) { index ->
                                left[destination + index] += block[0][index] * track.gain
                                right[destination + index] += block[1][index] * track.gain
                            }
                        }
                    }

                    repeat(count) { index ->
                        left[index] = left[index].coerceIn(-1f, 1f)
                        right[index] = right[index].coerceIn(-1f, 1f)
                    }
                    writer.write(left, right)
                    outputStart += count
                    onProgress((outputStart.toFloat() / outputFrames).coerceIn(0f, 1f))
                }
            }
            onProgress(1f)
            return outputFile
        } finally {
            opened.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private data class OpenTrack(
        val source: Pcm16WavSampleSource,
        val gain: Float,
        val offsetFrames: Int,
        val frameCount: Int,
    )
}
