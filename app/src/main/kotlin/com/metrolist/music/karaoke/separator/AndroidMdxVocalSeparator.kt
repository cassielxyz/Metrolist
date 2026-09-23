/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import android.content.Context
import com.metrolist.music.karaoke.audio.AndroidPcmFileDecoder
import com.metrolist.music.karaoke.audio.KaraVoxAudioMaterializer
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
 * Fully local KaraVox separator: materialize -> streaming Android decode -> bounded-memory
 * STFT/ONNX MDX -> streaming PCM16 WAV stems. No source or microphone audio leaves the device.
 */
class AndroidMdxVocalSeparator(
    context: Context,
    private val modelManager: KaraVoxModelManager = KaraVoxModelManager(context),
    private val materializer: KaraVoxAudioMaterializer = KaraVoxAudioMaterializer(context),
    private val decoder: AndroidPcmFileDecoder = AndroidPcmFileDecoder(),
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

        val identity = fingerprint(audio.cacheKey)
        val workDir = File(appContext.cacheDir, "karaoke/separation-temp/$identity-${modelSpec.id}")
        val decodedFile = File(appContext.cacheDir, "karaoke/decode-temp/$identity.pcm16.wav")
        withContext(Dispatchers.IO) {
            workDir.deleteRecursively()
            check(workDir.mkdirs()) { "Unable to create KaraVox separation output directory" }
            decodedFile.parentFile?.mkdirs()
            decodedFile.delete()
        }

        try {
            onProgress(0.09f)
            decoder.decode(localSource, decodedFile) { decodeProgress ->
                onProgress(0.09f + decodeProgress.coerceIn(0f, 1f) * 0.11f)
            }
            currentCoroutineContext().ensureActive()

            val callerContext = currentCoroutineContext()
            val separated = withContext(Dispatchers.Default) {
                OnnxMdxModelRunner(installedModel, modelSpec).use { runner ->
                    StreamingMdxDemixer(mdxSpec, runner::run).separate(
                        decodedPcmWav = decodedFile,
                        outputDir = workDir,
                        targetSampleRateHz = modelSpec.sampleRateHz,
                    ) { progress ->
                        callerContext.ensureActive()
                        onProgress(0.20f + progress.coerceIn(0f, 1f) * 0.78f)
                    }
                }
            }
            callerContext.ensureActive()
            check(separated.instrumental.isFile && separated.instrumental.length() > 44L)
            check(separated.vocals.isFile && separated.vocals.length() > 44L)
            onProgress(1f)

            return AudioStemSet(
                instrumentalUri = separated.instrumental.toURI().toString(),
                vocalsUri = separated.vocals.toURI().toString(),
                sourceFingerprint = identity,
            )
        } finally {
            withContext(Dispatchers.IO) { decodedFile.delete() }
        }
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
