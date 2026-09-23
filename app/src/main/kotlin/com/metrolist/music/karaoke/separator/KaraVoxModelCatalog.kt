/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.model.SeparationQuality

enum class PredictedStem {
    VOCALS,
    INSTRUMENTAL,
}

data class MdxDspSpec(
    val nFft: Int,
    val hopLength: Int,
    val dimF: Int,
    val dimT: Int,
    val compensation: Float,
    val predictedStem: PredictedStem,
)

data class SeparatorModelSpec(
    val id: String,
    val displayName: String,
    val quality: SeparationQuality,
    val fileName: String,
    val downloadUrl: String?,
    val sha256: String?,
    val expectedBytes: Long?,
    val sampleRateHz: Int,
    val mdx: MdxDspSpec?,
    val licenseName: String,
    val attribution: String,
    val sourceUrl: String,
)

/**
 * Primary models here split a full mix into vocals + instrumental. The UVR KARA/KARA_2 models
 * are deliberately not used as the primary separator because they are intended for karaoke /
 * lead-vs-backing-vocal workflows rather than being our only full-mix separation stage.
 */
object KaraVoxModelCatalog {
    val FAST = SeparatorModelSpec(
        id = "uvr-mdxnet-3-9662",
        displayName = "UVR MDX-Net 3",
        quality = SeparationQuality.FAST,
        fileName = "UVR_MDXNET_3_9662.onnx",
        downloadUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_3_9662.onnx",
        sha256 = "e02220e80d8253f4c2209f8924298b2b686bbdf2868b788ff5500fb9bd94aadc",
        expectedBytes = 29_700_000L,
        sampleRateHz = 44_100,
        mdx = MdxDspSpec(
            nFft = 6_144,
            hopLength = 1_024,
            dimF = 2_048,
            dimT = 256,
            compensation = 1.035f,
            predictedStem = PredictedStem.VOCALS,
        ),
        licenseName = "MIT (UVR model attribution required)",
        attribution = "Ultimate Vocal Remover (UVR) developers; MDX-Net architecture by KUIELab.",
        sourceUrl = "https://github.com/Anjok07/ultimatevocalremovergui",
    )

    val BALANCED = SeparatorModelSpec(
        id = "uvr-mdxnet-inst-hq3",
        displayName = "UVR MDX-Net Instrumental HQ 3",
        quality = SeparationQuality.BALANCED,
        fileName = "UVR-MDX-NET-Inst_HQ_3.onnx",
        downloadUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR-MDX-NET-Inst_HQ_3.onnx",
        sha256 = "317554b07fe1ea5279a77f2b1520a41ea4b93432560c4ffd08792c30fddf9adc",
        expectedBytes = 66_800_000L,
        sampleRateHz = 44_100,
        mdx = MdxDspSpec(
            nFft = 6_144,
            hopLength = 1_024,
            dimF = 3_072,
            dimT = 256,
            compensation = 1.022f,
            predictedStem = PredictedStem.INSTRUMENTAL,
        ),
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
        mdx = null,
        licenseName = "User supplied",
        attribution = "KaraVox does not redistribute this model.",
        sourceUrl = "",
    )

    /** Optional second-stage model used later for lead-vs-backing vocal workflows. */
    val BACKING_VOCALS = SeparatorModelSpec(
        id = "uvr-mdxnet-kara2-secondary",
        displayName = "UVR MDX-Net Karaoke 2 (backing vocals)",
        quality = SeparationQuality.BALANCED,
        fileName = "UVR_MDXNET_KARA_2.onnx",
        downloadUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_KARA_2.onnx",
        sha256 = "bf32e15105a09c0f7dddd2b67346146334d6f3ecb399ed7638eba2ab07cbf5f4",
        expectedBytes = 52_786_726L,
        sampleRateHz = 44_100,
        mdx = MdxDspSpec(
            nFft = 5_120,
            hopLength = 1_024,
            dimF = 2_048,
            dimT = 256,
            compensation = 1.065f,
            predictedStem = PredictedStem.INSTRUMENTAL,
        ),
        licenseName = "MIT (UVR model attribution required)",
        attribution = "Ultimate Vocal Remover (UVR) developers; MDX-Net architecture by KUIELab.",
        sourceUrl = "https://github.com/Anjok07/ultimatevocalremovergui",
    )

    val primary = listOf(FAST, BALANCED, BEST_IMPORT_ONLY)
    val all = primary + BACKING_VOCALS

    fun forQuality(quality: SeparationQuality): SeparatorModelSpec = when (quality) {
        SeparationQuality.FAST -> FAST
        SeparationQuality.BALANCED -> BALANCED
        SeparationQuality.BEST -> BEST_IMPORT_ONLY
    }
}
