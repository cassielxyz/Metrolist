package com.metrolist.music.karaoke.separation

import com.metrolist.music.karaoke.model.KaraokeStemSet
import com.metrolist.music.karaoke.model.SeparationQuality

data class SeparationRequest(
    val sourceUri: String,
    val sourceFingerprint: String,
    val quality: SeparationQuality,
)

sealed interface SeparationProgress {
    data object Queued : SeparationProgress
    data class Running(val fraction: Float, val stage: String) : SeparationProgress
    data class Complete(val stems: KaraokeStemSet) : SeparationProgress
    data class Failed(val reason: String, val retryable: Boolean) : SeparationProgress
}

/** Boundary for on-device two-stem separation. UI and session code must not depend on a model runtime. */
interface VocalSeparator {
    val modelId: String
    suspend fun cachedResult(request: SeparationRequest): KaraokeStemSet?
    fun separate(request: SeparationRequest): kotlinx.coroutines.flow.Flow<SeparationProgress>
    suspend fun cancel(sourceFingerprint: String)
}
