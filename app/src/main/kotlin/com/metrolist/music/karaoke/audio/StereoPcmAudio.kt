/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.audio

import kotlin.math.floor
import kotlin.math.roundToInt

data class StereoPcmAudio(
    val sampleRateHz: Int,
    val left: FloatArray,
    val right: FloatArray,
) {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive" }
        require(left.size == right.size) { "Stereo channels must have equal lengths" }
    }

    val sampleCount: Int
        get() = left.size

    val durationMs: Long
        get() = if (sampleRateHz == 0) 0L else sampleCount * 1_000L / sampleRateHz

    fun asChannels(): Array<FloatArray> = arrayOf(left, right)

    fun resample(targetSampleRateHz: Int): StereoPcmAudio {
        require(targetSampleRateHz > 0) { "Target sample rate must be positive" }
        if (targetSampleRateHz == sampleRateHz || sampleCount == 0) return this

        val outputLength = (sampleCount.toDouble() * targetSampleRateHz / sampleRateHz)
            .roundToInt()
            .coerceAtLeast(1)
        val ratio = sampleRateHz.toDouble() / targetSampleRateHz.toDouble()

        fun resampleChannel(input: FloatArray): FloatArray = FloatArray(outputLength) { outputIndex ->
            val sourcePosition = outputIndex * ratio
            val lower = floor(sourcePosition).toInt().coerceIn(0, input.lastIndex)
            val upper = (lower + 1).coerceAtMost(input.lastIndex)
            val fraction = (sourcePosition - lower).toFloat()
            (input[lower] * (1f - fraction) + input[upper] * fraction).coerceIn(-1f, 1f)
        }

        return StereoPcmAudio(
            sampleRateHz = targetSampleRateHz,
            left = resampleChannel(left),
            right = resampleChannel(right),
        )
    }
}

/** Chunked builder avoids repeated full-array reallocations while MediaCodec is decoding. */
internal class FloatPcmChannelBuilder(
    private val blockSize: Int = 16_384,
) {
    private val blocks = mutableListOf<FloatArray>()
    private var current = FloatArray(blockSize)
    private var currentSize = 0
    private var totalSize = 0

    fun add(value: Float) {
        if (currentSize == current.size) {
            blocks += current
            current = FloatArray(blockSize)
            currentSize = 0
        }
        current[currentSize++] = value.coerceIn(-1f, 1f)
        totalSize += 1
    }

    fun toFloatArray(): FloatArray {
        val output = FloatArray(totalSize)
        var offset = 0
        blocks.forEach { block ->
            block.copyInto(output, destinationOffset = offset)
            offset += block.size
        }
        current.copyInto(output, destinationOffset = offset, endIndex = currentSize)
        return output
    }
}
