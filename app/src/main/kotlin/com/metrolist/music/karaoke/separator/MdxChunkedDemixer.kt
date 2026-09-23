/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt

/** Output of one full-track MDX separation before files are encoded. */
data class MdxSeparatedAudio(
    val instrumental: Array<FloatArray>,
    val vocals: Array<FloatArray>,
)

/**
 * Runs classic MDX-Net inference over a full stereo track using overlapping windows.
 *
 * The padding/trimming follows UVR's MDX flow: prepend `nFft/2`, infer fixed-size windows,
 * overlap-add them with a symmetric Hann window, then remove the prepended trim and crop to the
 * exact source length. The model's predicted stem receives its published compensation factor;
 * the complementary stem is produced by subtraction from the original mix.
 */
class MdxChunkedDemixer(
    private val spec: MdxDspSpec,
    private val runModel: (FloatArray) -> FloatArray,
) {
    private val processor = MdxSpectrogramProcessor(spec)

    fun separate(
        mix: Array<FloatArray>,
        onProgress: (Float) -> Unit = {},
    ): MdxSeparatedAudio {
        require(mix.size == 2) { "KaraVox MDX separation requires stereo audio" }
        require(mix[0].size == mix[1].size) { "Stereo channels must have equal sample counts" }
        if (mix[0].isEmpty()) {
            return MdxSeparatedAudio(Array(2) { FloatArray(0) }, Array(2) { FloatArray(0) })
        }

        val sourceLength = mix[0].size
        val trim = spec.nFft / 2
        val chunkSize = processor.chunkSize
        val generationSize = chunkSize - 2 * trim
        require(generationSize > 0) {
            "Invalid MDX configuration: chunk $chunkSize must exceed nFft ${spec.nFft}"
        }
        require(spec.overlap in 0f..<1f) { "MDX overlap must be in [0, 1)" }

        val remainder = sourceLength % generationSize
        val pad = generationSize + trim - remainder
        val mixtureLength = trim + sourceLength + pad
        val mixture = Array(2) { FloatArray(mixtureLength) }
        for (channel in 0..1) {
            mix[channel].copyInto(mixture[channel], destinationOffset = trim)
        }

        val step = ((1f - spec.overlap) * chunkSize)
            .roundToInt()
            .coerceIn(1, chunkSize)
        val totalChunks = ceil(mixtureLength.toDouble() / step.toDouble()).toInt().coerceAtLeast(1)
        val accumulated = Array(2) { FloatArray(mixtureLength) }
        val divider = FloatArray(mixtureLength)

        var chunkNumber = 0
        var start = 0
        while (start < mixtureLength) {
            val end = minOf(start + chunkSize, mixtureLength)
            val actualLength = end - start
            val inputChunk = Array(2) { channel ->
                FloatArray(chunkSize).also { chunk ->
                    mixture[channel].copyInto(
                        destination = chunk,
                        destinationOffset = 0,
                        startIndex = start,
                        endIndex = end,
                    )
                }
            }

            val spectrum = processor.forward(inputChunk, zeroLowestBins = 3)
            val predictedSpectrum = runModel(spectrum)
            require(predictedSpectrum.size == processor.tensorElementCount) {
                "MDX model returned ${predictedSpectrum.size} values; expected ${processor.tensorElementCount}"
            }
            val predictedChunk = processor.inverse(predictedSpectrum)
            val overlapWindow = if (spec.overlap == 0f) null else symmetricHann(actualLength)

            for (sample in 0 until actualLength) {
                val weight = overlapWindow?.get(sample) ?: 1f
                divider[start + sample] += weight
                for (channel in 0..1) {
                    accumulated[channel][start + sample] += predictedChunk[channel][sample] * weight
                }
            }

            chunkNumber += 1
            onProgress((chunkNumber.toFloat() / totalChunks).coerceIn(0f, 1f))
            start += step
        }

        val predicted = Array(2) { channel ->
            FloatArray(sourceLength) { index ->
                val paddedIndex = index + trim
                val weight = divider[paddedIndex]
                val value = if (weight > 1e-8f) {
                    accumulated[channel][paddedIndex] / weight
                } else {
                    0f
                }
                (value * spec.compensation).coerceIn(-1f, 1f)
            }
        }
        val complement = Array(2) { channel ->
            FloatArray(sourceLength) { index ->
                (mix[channel][index] - predicted[channel][index]).coerceIn(-1f, 1f)
            }
        }

        onProgress(1f)
        return when (spec.predictedStem) {
            PredictedStem.VOCALS -> MdxSeparatedAudio(
                instrumental = complement,
                vocals = predicted,
            )
            PredictedStem.INSTRUMENTAL -> MdxSeparatedAudio(
                instrumental = predicted,
                vocals = complement,
            )
        }
    }

    private fun symmetricHann(length: Int): FloatArray {
        if (length <= 1) return FloatArray(length) { 1f }
        return FloatArray(length) { index ->
            (0.5 - 0.5 * cos(2.0 * PI * index / (length - 1))).toFloat()
        }
    }
}
