package com.metrolist.music.karaoke.separator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MdxSpectrogramProcessorTest {
    private val dsp = MdxDspSpec(
        nFft = 64,
        hopLength = 16,
        dimF = 33,
        dimT = 17,
        compensation = 1f,
        predictedStem = PredictedStem.VOCALS,
    )

    @Test
    fun exposesExpectedChunkAndTensorSizes() {
        val processor = MdxSpectrogramProcessor(dsp)
        assertEquals(256, processor.chunkSize)
        assertEquals(4 * 33 * 17, processor.tensorElementCount)
    }

    @Test
    fun fullBandForwardInverseRoundTripPreservesStereoWaveform() {
        val processor = MdxSpectrogramProcessor(dsp)
        val left = FloatArray(processor.chunkSize) { index ->
            (0.45 * sin(2.0 * PI * 5.0 * index / 256.0)).toFloat()
        }
        val right = FloatArray(processor.chunkSize) { index ->
            (0.30 * sin(2.0 * PI * 11.0 * index / 256.0)).toFloat()
        }

        val tensor = processor.forward(arrayOf(left, right), zeroLowestBins = 0)
        val reconstructed = processor.inverse(tensor)

        val maxLeftError = left.indices.maxOf { abs(left[it] - reconstructed[0][it]).toDouble() }
        val maxRightError = right.indices.maxOf { abs(right[it] - reconstructed[1][it]).toDouble() }
        assertTrue("left max error=$maxLeftError", maxLeftError < 1e-4)
        assertTrue("right max error=$maxRightError", maxRightError < 1e-4)
    }

    @Test
    fun lowFrequencySuppressionZerosConfiguredBinsForBothChannels() {
        val processor = MdxSpectrogramProcessor(dsp)
        val stereo = Array(2) { FloatArray(processor.chunkSize) { 1f } }
        val tensor = processor.forward(stereo, zeroLowestBins = 3)

        for (plane in 0 until 4) {
            for (frequency in 0 until 3) {
                for (time in 0 until dsp.dimT) {
                    val index = (plane * dsp.dimF + frequency) * dsp.dimT + time
                    assertEquals(0f, tensor[index], 0f)
                }
            }
        }
    }
}
