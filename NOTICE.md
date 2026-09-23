# KaraVox — Open-source attribution

KaraVox is an independent karaoke-focused Android project licensed under GPL-3.0.

## Upstream foundation

KaraVox is derived from the open-source Metrolist codebase and retains substantial portions of its Android architecture, media playback/search infrastructure, settings foundation, integrations, and project history.

Upstream project:

- Metrolist — https://github.com/MetrolistGroup/Metrolist

Original Metrolist authors and contributors retain copyright and attribution for their work. KaraVox does not claim authorship of inherited upstream code.

## Vocal-separation models

KaraVox's built-in model catalog is designed to download models at runtime instead of bundling large weights in the APK.

The current primary full-mix catalog entries are:

- `UVR_MDXNET_3_9662.onnx` — Fast profile, predicts the vocal stem; the instrumental is reconstructed as the residual.
- `UVR-MDX-NET-Inst_HQ_3.onnx` — Balanced profile, predicts the instrumental stem directly.

An optional `UVR_MDXNET_KARA_2.onnx` entry is reserved as a **secondary lead-vs-backing-vocal stage** and is not treated as the main full-mix separator.

Credit: Ultimate Vocal Remover (UVR) developers and the KUIELab MDX-Net architecture authors. UVR's project documentation asks third-party applications using its models to honor the MIT license and credit UVR and its developers. KaraVox stores SHA-256 pins for supported model files and verifies them before use.

KaraVox does not automatically redistribute model weights without an explicit licensing basis. The `BEST` quality slot remains import-only until a higher-quality Android-suitable model is verified for redistribution, memory use, thermal behavior and performance.

## Other important upstream/integrated work

The repository also contains or depends on open-source work including, but not limited to:

- InnerTune / related YouTube Music client foundations
- OuterTune-derived work
- Better Lyrics
- LRCLIB
- AndroidX Media3
- Jetpack Compose
- Kotlin coroutines
- Room
- Ktor
- Hilt

Refer to the repository's dependency files, source headers, Git history, and individual module licenses for complete licensing information.

## KaraVox-specific work

KaraVox adds and is developing a dedicated karaoke product layer including:

- karaoke-first navigation and UI
- online/local karaoke preparation pipeline
- pluggable vocal-separation architecture
- verified runtime model management
- word-aware karaoke lyric model
- private local recording architecture
- recording-first private duet workflow
- post-record timing correction and alignment architecture
- karaoke-specific settings, caching, mixing, and export systems

KaraVox is not affiliated with YouTube, Google, Ultimate Vocal Remover, KUIELab, or the Metrolist project maintainers.
