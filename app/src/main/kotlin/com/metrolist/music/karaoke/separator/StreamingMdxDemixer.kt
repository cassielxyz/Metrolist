package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.audio.Pcm16WavSampleSource
import com.metrolist.music.karaoke.audio.Pcm16WavStreamWriter
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt

data class MdxSeparatedFiles(
    val instrumental: File,
    val vocals: File,
)

/**
 * File-backed MDX overlap-add pipeline. Memory use is bounded by a few model windows rather than
 * by song duration, which keeps multi-minute songs viable on Android devices.
 */
class StreamingMdxDemixer(
    private val spec: MdxDspSpec,
    private val runModel: (FloatArray) -> FloatArray,
) {
    private val processor = MdxSpectrogramProcessor(spec)

    fun separate(
        decodedPcmWav: File,
        outputDir: File,
        targetSampleRateHz: Int,
        onProgress: (Float) -> Unit = {},
    ): MdxSeparatedFiles {
        require(spec.overlap in 0f..<1f) { "MDX overlap must be in [0, 1)" }
        require(targetSampleRateHz > 0)
        outputDir.mkdirs()
        val instrumentalFile = outputDir.resolve("instrumental.wav")
        val vocalsFile = outputDir.resolve("vocals.wav")
        instrumentalFile.delete()
        vocalsFile.delete()

        Pcm16WavSampleSource(decodedPcmWav).use { source ->
            val sourceLength = source.frameCountAt(targetSampleRateHz)
            require(sourceLength > 1) { "Decoded audio is too short for vocal separation" }
            val trim = spec.nFft / 2
            val chunkSize = processor.chunkSize
            val generationSize = chunkSize - 2 * trim
            require(generationSize > 0) {
                "Invalid MDX configuration: chunk $chunkSize must exceed nFft ${spec.nFft}"
            }
            val remainder = sourceLength % generationSize
            val pad = generationSize + trim - remainder
            val mixtureLength = trim + sourceLength + pad
            val step = ((1f - spec.overlap) * chunkSize)
                .roundToInt()
                .coerceIn(1, chunkSize)
            val totalChunks = ceil(mixtureLength.toDouble() / step.toDouble()).toInt().coerceAtLeast(1)

            val accumulated = Array(2) { FloatArray(chunkSize) }
            val divider = FloatArray(chunkSize)
            var bufferStart = 0
            var bufferValid = 0
            var writtenSourceFrames = 0

            Pcm16WavStreamWriter(instrumentalFile, targetSampleRateHz).use { instrumentalWriter ->
                Pcm16WavStreamWriter(vocalsFile, targetSampleRateHz).use { vocalWriter ->
                    var chunkNumber = 0
                    var start = 0
                    while (start < mixtureLength) {
                        check(bufferStart == start) {
                            "Streaming overlap buffer lost synchronization ($bufferStart != $start)"
                        }
                        val actualLength = minOf(chunkSize, mixtureLength - start)
                        val inputChunk = readPaddedChunk(
                            source = source,
                            paddedStart = start,
                            length = chunkSize,
                            trim = trim,
                            sourceLength = sourceLength,
                            targetSampleRateHz = targetSampleRateHz,
                        )
                        val spectrum = processor.forward(inputChunk, zeroLowestBins = 0)
                        val predictedSpectrum = runModel(spectrum)
                        require(predictedSpectrum.size == processor.tensorElementCount) {
                            "MDX model returned ${predictedSpectrum.size} values; expected ${processor.tensorElementCount}"
                        }
                        val predictedChunk = processor.inverse(predictedSpectrum)
                        val overlapWindow = if (spec.overlap == 0f) null else symmetricHann(actualLength)

                        for (sample in 0 until actualLength) {
                            val weight = overlapWindow?.get(sample) ?: 1f
                            divider[sample] += weight
                            for (channel in 0..1) {
                                accumulated[channel][sample] += predictedChunk[channel][sample] * weight
                            }
                        }
                        bufferValid = maxOf(bufferValid, actualLength)

                        val flushUntil = minOf(start + step, mixtureLength)
                        val flushCount = flushUntil - bufferStart
                        for (local in 0 until flushCount) {
                            val global = bufferStart + local
                            if (global >= trim && global < trim + sourceLength) {
                                val weight = divider[local]
                                val predictedLeft = if (weight > 1e-8f) {
                                    (accumulated[0][local] / weight * spec.compensation).coerceIn(-1f, 1f)
                                } else 0f
                                val predictedRight = if (weight > 1e-8f) {
                                    (accumulated[1][local] / weight * spec.compensation).coerceIn(-1f, 1f)
                                } else 0f
                                val sourceLeft = inputChunk[0][local]
                                val sourceRight = inputChunk[1][local]
                                val complementLeft = (sourceLeft - predictedLeft).coerceIn(-1f, 1f)
                                val complementRight = (sourceRight - predictedRight).coerceIn(-1f, 1f)
                                when (spec.predictedStem) {
                                    PredictedStem.VOCALS -> {
                                        vocalWriter.writeFrame(predictedLeft, predictedRight)
                                        instrumentalWriter.writeFrame(complementLeft, complementRight)
                                    }
                                    PredictedStem.INSTRUMENTAL -> {
                                        instrumentalWriter.writeFrame(predictedLeft, predictedRight)
                                        vocalWriter.writeFrame(complementLeft, complementRight)
                                    }
                                }
                                writtenSourceFrames += 1
                            }
                        }

                        shiftLeft(accumulated, divider, flushCount, bufferValid)
                        bufferValid = (bufferValid - flushCount).coerceAtLeast(0)
                        bufferStart = flushUntil
                        chunkNumber += 1
                        onProgress((chunkNumber.toFloat() / totalChunks).coerceIn(0f, 1f))
                        start += step
                    }
                }
            }
            check(writtenSourceFrames == sourceLength) {
                "Streaming separator wrote $writtenSourceFrames frames; expected $sourceLength"
            }
        }
        onProgress(1f)
        return MdxSeparatedFiles(instrumentalFile, vocalsFile)
    }

    private fun readPaddedChunk(
        source: Pcm16WavSampleSource,
        paddedStart: Int,
        length: Int,
        trim: Int,
        sourceLength: Int,
        targetSampleRateHz: Int,
    ): Array<FloatArray> {
        val output = Array(2) { FloatArray(length) }
        val sourceStart = paddedStart - trim
        val destinationStart = maxOf(0, -sourceStart)
        val firstSourceFrame = maxOf(0, sourceStart)
        val available = minOf(
            length - destinationStart,
            sourceLength - firstSourceFrame,
        ).coerceAtLeast(0)
        if (available > 0) {
            val block = source.readResampled(firstSourceFrame, available, targetSampleRateHz)
            for (channel in 0..1) {
                block[channel].copyInto(output[channel], destinationOffset = destinationStart)
            }
        }
        return output
    }

    private fun shiftLeft(
        accumulated: Array<FloatArray>,
        divider: FloatArray,
        count: Int,
        validLength: Int,
    ) {
        if (count <= 0) return
        val remaining = (validLength - count).coerceAtLeast(0)
        if (remaining > 0) {
            divider.copyInto(divider, 0, count, count + remaining)
            for (channel in 0..1) {
                accumulated[channel].copyInto(accumulated[channel], 0, count, count + remaining)
            }
        }
        divider.fill(0f, remaining, divider.size)
        for (channel in 0..1) accumulated[channel].fill(0f, remaining, accumulated[channel].size)
    }

    private fun symmetricHann(length: Int): FloatArray {
        if (length <= 1) return FloatArray(length) { 1f }
        return FloatArray(length) { index ->
            (0.5 - 0.5 * cos(2.0 * PI * index / (length - 1))).toFloat()
        }
    }
}
