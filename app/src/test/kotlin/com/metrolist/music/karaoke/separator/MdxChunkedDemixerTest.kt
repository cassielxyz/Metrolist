package com.metrolist.music.karaoke.separator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MdxChunkedDemixerTest {
    @Test
    fun identityModelProducesPredictedVocalAndNearSilentInstrumental() {
        val spec = MdxDspSpec(
            nFft = 64,
            hopLength = 16,
            dimF = 33,
            dimT = 17,
            compensation = 1f,
            predictedStem = PredictedStem.VOCALS,
            overlap = 0.25f,
        )
        val length = 1_137
        val left = FloatArray(length) { index ->
            (0.35 * sin(2.0 * PI * 29.0 * index / 512.0)).toFloat()
        }
        val right = FloatArray(length) { index ->
            (0.28 * sin(2.0 * PI * 41.0 * index / 512.0)).toFloat()
        }
        val progress = mutableListOf<Float>()
        val demixer = MdxChunkedDemixer(spec) { tensor -> tensor.copyOf() }

        val result = demixer.separate(arrayOf(left, right), progress::add)

        assertEquals(length, result.vocals[0].size)
        assertEquals(length, result.instrumental[0].size)
        val vocalError = left.indices.maxOf { abs(left[it] - result.vocals[0][it]).toDouble() }
        val instrumentalPeak = result.instrumental[0].maxOf { abs(it).toDouble() }
        assertTrue("vocal max error=$vocalError", vocalError < 1e-3)
        assertTrue("instrumental peak=$instrumentalPeak", instrumentalPeak < 1e-3)
        assertTrue(progress.isNotEmpty())
        assertEquals(1f, progress.last(), 0f)
    }

    @Test
    fun instrumentalPredictionMapsToInstrumentalOutput() {
        val spec = MdxDspSpec(
            nFft = 64,
            hopLength = 16,
            dimF = 33,
            dimT = 17,
            compensation = 1f,
            predictedStem = PredictedStem.INSTRUMENTAL,
            overlap = 0f,
        )
        val signal = FloatArray(700) { index ->
            (0.2 * sin(2.0 * PI * 23.0 * index / 512.0)).toFloat()
        }
        val demixer = MdxChunkedDemixer(spec) { tensor -> tensor.copyOf() }

        val result = demixer.separate(arrayOf(signal, signal.copyOf()))

        val error = signal.indices.maxOf { abs(signal[it] - result.instrumental[0][it]).toDouble() }
        assertTrue("instrumental max error=$error", error < 1e-3)
    }
}
