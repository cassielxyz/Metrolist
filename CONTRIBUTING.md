# Contributing to KaraVox

KaraVox is a privacy-first Android karaoke and recording project. Contributions are welcome, but every change must preserve the project identity, upstream GPL attribution, user privacy, and the recording-first architecture documented in `docs/KARAOKE_ARCHITECTURE.md`.

## Before opening a pull request

1. Create a focused branch such as `feature/...`, `fix/...`, or `refactor/...`.
2. Do not commit API keys, signing keys, private keys, downloaded ONNX/model weights, APKs, or personal recordings.
3. Keep vocal-separation models runtime-downloaded/imported and verify redistribution rights before adding a built-in model source.
4. Do not silently upload recordings or microphone data. Any sharing path must require explicit user action.
5. Preserve `NOTICE.md` and upstream license/attribution requirements.

## Required local verification

From the repository root:

```bash
bash scripts/ci/verify_karavox_project.sh
./gradlew --console=plain :app:testFossDebugUnitTest :app:lintFossDebug assembleFossDebug
```

A pull request automatically runs the same core checks through **KaraVox Contributor Verification**. The workflow validates repository policy, Gradle wrapper/build configuration, unit tests, Android lint, and the FOSS debug APK. Manual and scheduled runs additionally build the FOSS, GMS, and Izzy debug variants.

## Karaoke-specific testing expectations

Changes to parsing, synchronization, separation, recording, alignment, caching, or export logic should include deterministic unit tests whenever possible. Hardware-dependent microphone, latency, thermal, and model-inference changes should document the Android device/API level used for validation.

## Pull request notes

Explain what changed, how it was verified, whether it affects privacy/storage/network use, and whether it introduces or changes any third-party model/library license. Keep unrelated refactors in separate pull requests so failures are easier to isolate.
