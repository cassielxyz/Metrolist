<div align="center">

# KaraVox

### Private karaoke for Android

Search for a song or choose local audio, prepare an instrumental on-device, follow synced lyrics, and record solo or duet vocals privately.

<br/>

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)](LICENSE)

</div>

---

## KaraVox

KaraVox is a dedicated karaoke app, not a normal music player.

```text
Online song / Local audio
          ↓
     Prepare karaoke
          ↓
 On-device separation
   ├─ Instrumental
   └─ Vocal guide
          ↓
   Synced lyrics
          ↓
   Sing + record
          ↓
 Solo mix / private duet
```

Privacy is part of the product design: there is no social feed, public profile, follower system, or automatic recording upload. Microphone capture and stem separation are local. A duet vocal take leaves the device only when the user explicitly exports it.

## Included in the 1.0 release candidate

- Karaoke-only Home, Search, Library, Recordings and Settings experience
- Online song discovery through the inherited compatible media/search foundation
- Local audio selection through Android's document picker
- On-device ONNX/MDX vocal separation with verified Fast and Balanced model profiles
- Bounded-memory streaming decode and overlap-add separation for multi-minute audio
- SHA-256 verified runtime model downloads; model weights are not committed to the repository
- Persistent prepared-song/stem cache
- Better Lyrics and LRCLIB providers
- LRC, Enhanced LRC and hardened TTML parsing/import
- Fullscreen synced lyrics with word highlighting when word timing exists
- Persistent manual lyric timing offset
- Instrumental playback with optional 0–100% vocal guide
- Configurable recording countdown
- Private 48 kHz PCM16 WAV microphone recording
- Recording library, solo mix export and WAV export
- Private duet take export/import package
- Metadata + waveform-based post-record duet alignment
- Persistent manual partner correction with ±10 ms fine adjustment
- Local streaming duet mixer
- Storage manager for temporary sources, prepared stems, models and recordings
- TLS-only production network policy
- Automatic contributor verification for source policy, tests, debug/release lint and debug/release APK assembly
- Protected production release workflow with APK alignment, signing verification and SHA-256 output

## Private duet workflow

KaraVox intentionally does **not** stream both microphones live. Remote network/audio latency makes that unsuitable for a high-quality duet recording.

```text
Singer A prepares the song
Singer B prepares the same song
          ↓
Each records their microphone locally
          ↓
One singer exports a private KaraVox take
          ↓
The other imports it
          ↓
Automatic metadata + waveform alignment
          ↓
Optional manual timing correction
          ↓
Final local duet mix + WAV export
```

The exchanged duet package contains the singer's vocal take and synchronization metadata, not the prepared instrumental.

## Lyrics

KaraVox uses the best timing information available. Word-level timing is rendered when supplied by the lyric source; line-level timing remains the fallback. Supported paths include Better Lyrics, LRCLIB, local LRC/Enhanced LRC and TTML. A persistent millisecond offset lets the singer correct imperfect source timing.

## Vocal separation

KaraVox separates locally with ONNX Runtime. The release candidate supports two verified downloadable profiles:

- **Fast** — UVR MDX-Net 3
- **Balanced** — UVR MDX-Net Instrumental HQ 3

Models are downloaded at runtime over HTTPS and validated by expected size and SHA-256. KaraVox does not commit large model checkpoints into Git.

## Recording quality

The production baseline records the microphone independently from the instrumental as **48 kHz / 16-bit PCM WAV**. Keeping microphone and instrumental tracks separate allows non-destructive solo and duet alignment/mixing. Additional encoders can be added later without pretending that the current capture backend is 24-bit, FLAC, or AAC.

## Verification

Contributor pull requests automatically run:

```bash
bash scripts/ci/verify_karavox_project.sh
./gradlew \
  :app:testFossDebugUnitTest \
  :app:lintFossDebug \
  :app:lintFossRelease \
  :app:assembleFossDebug \
  :app:assembleFossRelease
```

The workflow uploads verification reports, APK artifacts and SHA-256 checksums. Production publishing uses a separate protected signing workflow. See `docs/RELEASE.md`.

## Build locally

KaraVox requires JDK 21 and the Android toolchain used by the project.

```bash
./gradlew assembleFossDebug
```

For a minified release candidate:

```bash
./gradlew assembleFossRelease lintFossRelease
```

## Architecture

```text
app/
└─ karaoke/
   ├─ audio/
   ├─ cache/
   ├─ domain/
   ├─ duet/
   ├─ lyrics/
   ├─ model/
   ├─ recording/
   ├─ separator/
   ├─ settings/
   └─ source/
```

See `docs/KARAOKE_ARCHITECTURE.md` for the detailed flow and design boundaries.

## Release-candidate validation

Automated CI verifies source policy, unit tests, lint and APK assembly, but audio quality, microphone behavior, model speed/thermal behavior and device-specific output latency must also be checked on real Android hardware before the `v1.0.0` production tag is published. The current version is therefore `1.0.0-rc1` until that device acceptance pass is completed.

## License and upstream attribution

KaraVox is licensed under **GPL-3.0** and preserves attribution for inherited open-source work. It derives substantial foundations from Metrolist and other projects listed in `NOTICE.md` and the dependency files. The original contributors retain credit for their work.

KaraVox is its own karaoke-focused product and is not affiliated with YouTube, Google, Ultimate Vocal Remover, KUIELab, Better Lyrics, LRCLIB, or Metrolist maintainers.

See `LICENSE` and `NOTICE.md`.
