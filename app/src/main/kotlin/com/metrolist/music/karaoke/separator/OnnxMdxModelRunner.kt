/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thin, validated ONNX Runtime wrapper for classic UVR MDX-Net spectrogram models.
 *
 * KaraVox keeps STFT/audio decoding outside the model runner. This class only accepts the dense
 * `[1, 4, dimF, dimT]` float tensor expected by the MDX ONNX models and returns the same layout.
 */
class OnnxMdxModelRunner(
    modelFile: File,
    private val spec: SeparatorModelSpec,
    threadCount: Int = defaultThreadCount(),
) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        setIntraOpNumThreads(threadCount.coerceAtLeast(1))
        setInterOpNumThreads(1)
    }
    private val session: OrtSession
    private val inputName: String
    private val outputName: String
    private val tensorShape: LongArray

    init {
        require(modelFile.isFile && modelFile.length() > 0L) {
            "ONNX model is missing or empty: ${modelFile.absolutePath}"
        }
        val mdx = requireNotNull(spec.mdx) {
            "${spec.displayName} does not define an MDX tensor layout"
        }
        tensorShape = longArrayOf(1L, 4L, mdx.dimF.toLong(), mdx.dimT.toLong())
        session = try {
            environment.createSession(modelFile.absolutePath, sessionOptions)
        } catch (error: Throwable) {
            sessionOptions.close()
            throw error
        }

        try {
            require(session.numInputs == 1L) {
                "Expected one MDX model input, found ${session.numInputs}"
            }
            require(session.numOutputs >= 1L) {
                "MDX model exposes no outputs"
            }

            inputName = session.inputNames.first()
            outputName = session.outputNames.first()
            validateTensorInfo(
                label = "input '$inputName'",
                info = session.inputInfo.getValue(inputName).info,
                expected = tensorShape,
                allowDynamic = true,
            )
            validateTensorInfo(
                label = "output '$outputName'",
                info = session.outputInfo.getValue(outputName).info,
                expected = tensorShape,
                allowDynamic = true,
            )
        } catch (error: Throwable) {
            session.close()
            sessionOptions.close()
            throw error
        }
    }

    val expectedElementCount: Int
        get() = tensorShape.fold(1L) { acc, value -> acc * value }
            .also { require(it <= Int.MAX_VALUE) }
            .toInt()

    /** Runs one already-prepared MDX spectrogram window. */
    fun run(input: FloatArray): FloatArray {
        require(input.size == expectedElementCount) {
            "MDX input has ${input.size} floats; expected $expectedElementCount"
        }

        val byteBuffer = ByteBuffer
            .allocateDirect(input.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(input)
        floatBuffer.flip()

        OnnxTensor.createTensor(environment, floatBuffer, tensorShape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                // Output names are ordered by ONNX output id. We validate the first output's
                // declared metadata during construction, so index access avoids Optional/JVM
                // interop ambiguity while keeping the contract deterministic.
                val outputTensor = result[0] as? OnnxTensor
                    ?: error("MDX output '$outputName' is not a tensor")
                validateTensorInfo(
                    label = "runtime output '$outputName'",
                    info = outputTensor.info,
                    expected = tensorShape,
                    allowDynamic = false,
                )
                val buffer = outputTensor.floatBuffer
                    ?: error("MDX output '$outputName' is not a float tensor")
                require(buffer.remaining() == expectedElementCount) {
                    "MDX output has ${buffer.remaining()} floats; expected $expectedElementCount"
                }
                return FloatArray(expectedElementCount).also(buffer::get)
            }
        }
    }

    override fun close() {
        session.close()
        sessionOptions.close()
    }

    private fun validateTensorInfo(
        label: String,
        info: Any,
        expected: LongArray,
        allowDynamic: Boolean,
    ) {
        val tensorInfo = info as? TensorInfo
            ?: error("MDX $label is not a dense tensor")
        require(tensorInfo.type == OnnxJavaType.FLOAT) {
            "MDX $label must be FLOAT, found ${tensorInfo.type}"
        }
        val actual = tensorInfo.shape
        require(actual.size == expected.size) {
            "MDX $label rank ${actual.size} does not match expected rank ${expected.size}"
        }
        actual.indices.forEach { index ->
            val actualDim = actual[index]
            val expectedDim = expected[index]
            val dynamic = allowDynamic && actualDim <= 0L
            require(dynamic || actualDim == expectedDim) {
                "MDX $label shape ${actual.contentToString()} does not match ${expected.contentToString()}"
            }
        }
    }

    companion object {
        private fun defaultThreadCount(): Int = Runtime.getRuntime()
            .availableProcessors()
            .coerceIn(1, 4)
    }
}
