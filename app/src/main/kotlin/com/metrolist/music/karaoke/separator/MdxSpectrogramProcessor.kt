/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos

/**
 * Pure-JVM STFT/ISTFT implementation matching the classic UVR MDX tensor layout.
 *
 * Input/output waveform layout is stereo `[2][samples]`. Spectrogram tensors are flattened
 * row-major `[1, 4, dimF, dimT]`: left real, left imaginary, right real, right imaginary.
 * The window is PyTorch-compatible periodic Hann and center padding uses reflection.
 */
class MdxSpectrogramProcessor(
    private val spec: MdxDspSpec,
) {
    private val nFft = spec.nFft
    private val hopLength = spec.hopLength
    private val dimF = spec.dimF
    private val dimT = spec.dimT
    private val nBins = nFft / 2 + 1
    private val fft = FloatFFT_1D(nFft.toLong())
    private val window = FloatArray(nFft) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / nFft)).toFloat()
    }

    init {
        require(nFft > 0 && nFft % 2 == 0) { "MDX nFft must be a positive even number" }
        require(hopLength > 0) { "MDX hop length must be positive" }
        require(dimT > 1) { "MDX dimT must be greater than one" }
        require(dimF in 1..nBins) { "MDX dimF $dimF exceeds FFT bins $nBins" }
    }

    val chunkSize: Int
        get() = hopLength * (dimT - 1)

    val tensorElementCount: Int
        get() = 4 * dimF * dimT

    fun forward(stereo: Array<FloatArray>, zeroLowestBins: Int = 3): FloatArray {
        requireStereoChunk(stereo)
        val output = FloatArray(tensorElementCount)
        val frame = FloatArray(nFft * 2)
        val centerPad = nFft / 2

        for (channel in 0..1) {
            val signal = stereo[channel]
            for (time in 0 until dimT) {
                frame.fill(0f)
                val frameStart = time * hopLength - centerPad
                for (sample in 0 until nFft) {
                    val sourceIndex = reflectIndex(frameStart + sample, signal.size)
                    frame[sample] = signal[sourceIndex] * window[sample]
                }

                fft.realForwardFull(frame)
                for (frequency in 0 until dimF) {
                    val real = if (frequency < zeroLowestBins) 0f else frame[2 * frequency]
                    val imaginary = if (frequency < zeroLowestBins) 0f else frame[2 * frequency + 1]
                    output[tensorIndex(channel * 2, frequency, time)] = real
                    output[tensorIndex(channel * 2 + 1, frequency, time)] = imaginary
                }
            }
        }
        return output
    }

    fun inverse(tensor: FloatArray): Array<FloatArray> {
        require(tensor.size == tensorElementCount) {
            "MDX tensor has ${tensor.size} values; expected $tensorElementCount"
        }

        val paddedLength = (dimT - 1) * hopLength + nFft
        val overlap = Array(2) { FloatArray(paddedLength) }
        val windowWeight = FloatArray(paddedLength)
        val spectrum = FloatArray(nFft * 2)

        // Window normalization is identical for both channels, so accumulate it once.
        for (time in 0 until dimT) {
            val outputStart = time * hopLength
            for (sample in 0 until nFft) {
                val w = window[sample]
                windowWeight[outputStart + sample] += w * w
            }
        }

        for (channel in 0..1) {
            for (time in 0 until dimT) {
                spectrum.fill(0f)

                // Positive frequencies including DC/Nyquist. Frequencies above dimF are zero,
                // matching UVR's frequency padding before torch.istft.
                for (frequency in 0 until dimF) {
                    spectrum[2 * frequency] = tensor[tensorIndex(channel * 2, frequency, time)]
                    spectrum[2 * frequency + 1] = tensor[tensorIndex(channel * 2 + 1, frequency, time)]
                }

                // Recreate the negative-frequency conjugate half required by complexInverse.
                val highestMirrorBin = minOf(dimF - 1, nFft / 2 - 1)
                for (frequency in 1..highestMirrorBin) {
                    val mirror = nFft - frequency
                    spectrum[2 * mirror] = spectrum[2 * frequency]
                    spectrum[2 * mirror + 1] = -spectrum[2 * frequency + 1]
                }

                fft.complexInverse(spectrum, true)
                val outputStart = time * hopLength
                for (sample in 0 until nFft) {
                    overlap[channel][outputStart + sample] += spectrum[2 * sample] * window[sample]
                }
            }
        }

        val centerPad = nFft / 2
        return Array(2) { channel ->
            FloatArray(chunkSize) { index ->
                val paddedIndex = index + centerPad
                val weight = windowWeight[paddedIndex]
                if (weight > 1e-8f) overlap[channel][paddedIndex] / weight else 0f
            }
        }
    }

    private fun tensorIndex(plane: Int, frequency: Int, time: Int): Int =
        (plane * dimF + frequency) * dimT + time

    private fun requireStereoChunk(stereo: Array<FloatArray>) {
        require(stereo.size == 2) { "MDX expects stereo audio" }
        require(stereo[0].size == chunkSize && stereo[1].size == chunkSize) {
            "MDX chunk must contain exactly $chunkSize samples per channel"
        }
    }

    /** Reflection padding equivalent to PyTorch's center=True STFT default pad mode. */
    private fun reflectIndex(rawIndex: Int, length: Int): Int {
        require(length > 1) { "MDX reflection padding requires at least two samples" }
        var index = rawIndex
        while (index < 0 || index >= length) {
            index = if (index < 0) {
                -index
            } else {
                2 * length - index - 2
            }
        }
        return index
    }
}
