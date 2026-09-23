/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.model.SeparationQuality

data class SeparatorModelSpec(
    val id: String,
    val displayName: String,
    val quality: SeparationQuality,
    val fileName: String,
    val downloadUrl: String?,
    val sha256: String?,
    val expectedBytes: Long?,
    val sampleRateHz: Int,
    val licenseName: String,
    val attribution: String,
    val sourceUrl: String,
)

/**
 * Only models with an explicit redistribution basis should ever have a built-in download URL.
 * Model files are downloaded at runtime instead of inflating the APK.
 */
object KaraVoxModelCatalog {
    val FAST = SeparatorModelSpec(
        id = "uvr-mdxnet-kara-v1",
        displayName = "UVR MDX-Net Karaoke",
        quality = SeparationQuality.FAST,
        fileName = "UVR_MDXNET_KARA.onnx",
        downloadUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_KARA.onnx",
        sha256 = "e3167c87333a48548413e972a286bf40bf5694001d2853861eb1435953f02d63",
        expectedBytes = 29_700_000L,
        sampleRateHz = 44_100,
        licenseName = "MIT (UVR model attribution required)",
        attribution = "Ultimate Vocal Remover (UVR) developers; MDX-Net architecture by KUIELab.",
        sourceUrl = "https://github.com/Anjok07/ultimatevocalremovergui",
    )

    val BALANCED = SeparatorModelSpec(
        id = "uvr-mdxnet-kara-v2",
        displayName = "UVR MDX-Net Karaoke 2",
        quality = SeparationQuality.BALANCED,
        fileName = "UVR_MDXNET_KARA_2.onnx",
        downloadUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_KARA_2.onnx",
        sha256 = "bf32e15105a09c0f7dddd2b67346146334d6f3ecb399ed7638eba2ab07cbf5f4",
        expectedBytes = 52_786_726L,
        sampleRateHz = 44_100,
        licenseName = "MIT (UVR model attribution required)",
        attribution = "Ultimate Vocal Remover (UVR) developers; MDX-Net architecture by KUIELab.",
        sourceUrl = "https://github.com/Anjok07/ultimatevocalremovergui",
    )

    /**
     * KaraVox deliberately does not ship a giant or ambiguously licensed 'Best' checkpoint.
     * A future model can be enabled here only after Android performance and redistribution
     * rights are verified.
     */
    val BEST_IMPORT_ONLY = SeparatorModelSpec(
        id = "best-import-only",
        displayName = "Best quality (import model)",
        quality = SeparationQuality.BEST,
        fileName = "karavox_best.onnx",
        downloadUrl = null,
        sha256 = null,
        expectedBytes = null,
        sampleRateHz = 44_100,
        licenseName = "User supplied",
        attribution = "KaraVox does not redistribute this model.",
        sourceUrl = "",
    )

    val all = listOf(FAST, BALANCED, BEST_IMPORT_ONLY)

    fun forQuality(quality: SeparationQuality): SeparatorModelSpec = when (quality) {
        SeparationQuality.FAST -> FAST
        SeparationQuality.BALANCED -> BALANCED
        SeparationQuality.BEST -> BEST_IMPORT_ONLY
    }
}
