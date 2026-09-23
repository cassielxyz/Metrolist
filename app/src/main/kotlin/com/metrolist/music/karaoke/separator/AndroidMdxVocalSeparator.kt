/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import android.content.Context
import com.metrolist.music.karaoke.audio.AndroidAudioDecoder
import com.metrolist.music.karaoke.audio.KaraVoxAudioMaterializer
import com.metrolist.music.karaoke.audio.Pcm16WavWriter
import com.metrolist.music.karaoke.audio.StereoPcmAudio
import com.metrolist.music.karaoke.domain.VocalSeparator
import com.metrolist.music.karaoke.model.AudioStemSet
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class SeparationModelMissingException(
    val model: SeparatorModelSpec,
) : IllegalStateException("${model.displayName} must be installed before separation")

/**
 * Fully local KaraVox separator: materialize -> Android decode -> resample -> STFT/ONNX MDX -> WAV.
 * No microphone or source audio leaves the device.
 */
class AndroidMdxVocalSeparator(
    context: Context,
    private val modelManager: KaraVoxModelManager = KaraVoxModelManager(context),
    private val materializer: KaraVoxAudioMaterializer = KaraVoxAudioMaterializer(context),
    private val decoder: AndroidAudioDecoder = AndroidAudioDecoder(),
) : VocalSeparator {
    private val appContext = context.applicationContext

    override suspend fun separate(
        audio: ResolvedKaraokeAudio,
        quality: SeparationQuality,
        onProgress: (Float) -> Unit,
    ): AudioStemSet {
        val modelSpec = KaraVoxModelCatalog.forQuality(quality)
        val mdxSpec = modelSpec.mdx
            ?: error("${modelSpec.displayName} is not configured for automatic MDX inference")
        val installedModel = when (val state = modelManager.inspect(modelSpec)) {
            is ModelInstallState.Ready -> state.file
            else -> throw SeparationModelMissingException(modelSpec)
        }

        onProgress(0f)
        val localSource = materializer.materialize(audio) { sourceProgress ->
            onProgress((sourceProgress * 0.08f).coerceIn(0f, 0.08f))
        }
        currentCoroutineContext().ensureActive()
        onProgress(0.09f)

        val decoded = decoder.decode(localSource)
        currentCoroutineContext().ensureActive()
        val prepared = if (decoded.sampleRateHz == modelSpec.sampleRateHz) {
            decoded
        } else {
            withContext(Dispatchers.Default) {
                decoded.resample(modelSpec.sampleRateHz)
            }
        }
        onProgress(0.15f)

        val coroutineContext = currentCoroutineContext()
        val separated = withContext(Dispatchers.Default) {
            OnnxMdxModelRunner(installedModel, modelSpec).use { runner ->
                MdxChunkedDemixer(mdxSpec, runner::run).separate(prepared.asChannels()) { progress ->
                    coroutineContext.ensureActive()
                    onProgress(0.15f + progress.coerceIn(0f, 1f) * 0.75f)
                }
            }
        }
        coroutineContext.ensureActive()

        val outputDir = File(
            appContext.cacheDir,
            "karaoke/separation-temp/${fingerprint(audio.cacheKey)}-${modelSpec.id}",
        )
        withContext(Dispatchers.IO) {
            outputDir.deleteRecursively()
            check(outputDir.mkdirs()) { "Unable to create KaraVox separation output directory" }
            Pcm16WavWriter.write(
                outputDir.resolve("instrumental.wav"),
                StereoPcmAudio(modelSpec.sampleRateHz, separated.instrumental[0], separated.instrumental[1]),
            )
            onProgress(0.95f)
            Pcm16WavWriter.write(
                outputDir.resolve("vocals.wav"),
                StereoPcmAudio(modelSpec.sampleRateHz, separated.vocals[0], separated.vocals[1]),
            )
        }
        onProgress(1f)

        return AudioStemSet(
            instrumentalUri = outputDir.resolve("instrumental.wav").toURI().toString(),
            vocalsUri = outputDir.resolve("vocals.wav").toURI().toString(),
            sourceFingerprint = fingerprint(audio.cacheKey),
        )
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
