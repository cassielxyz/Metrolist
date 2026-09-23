<div align="center">

# KaraVox

### Private, ad-free karaoke for Android

Search online or pick a local song, prepare an instrumental, follow synced lyrics, and record solo or duet vocals privately.

<br/>

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)](LICENSE)

</div>

---

## What is KaraVox?

KaraVox is a dedicated karaoke app, not a normal music player.

The target flow is simple:

```text
Online song / Local song
          ↓
     Prepare karaoke
          ↓
  Vocal separation
   ├─ Instrumental
   └─ Lead vocals
          ↓
   Synced lyrics
          ↓
   Sing + record
```

The project is designed around privacy:

- no social feed
- no public profile
- no followers
- no automatic upload of recordings
- local-first vocal separation and recording
- recordings stay on-device unless the user explicitly shares a duet take or export

---

## Core features

### Song selection

- Online song search using the existing YouTube Music-compatible playback/search foundation
- Local/offline audio selection from device storage
- Prepared-song cache so vocal separation does not need to run every time

### Karaoke preparation

- Pluggable vocal-separation engine
- Fast / Balanced / Best quality profiles
- Separate instrumental and vocal stems
- Source fingerprinting and reusable stem cache
- Preparation progress and cancellation support

### Lyrics

- Better Lyrics integration
- LRCLIB fallback
- LRC and Enhanced LRC support
- TTML import and timed-span mapping
- Word-by-word highlighting when timing is available
- Line-level fallback when word timing is unavailable
- Manual lyric offset and sync correction

### Recording

- Separate microphone recording from instrumental playback
- Standard / High / Studio recording profiles in the domain model
- Verified PCM16 WAV capture baseline
- Optional future AAC / FLAC encoder backends
- Non-destructive mixing architecture

### Private duet mode

KaraVox does not try to stream two live microphones over the internet and pretend latency does not exist.

Instead:

```text
Both phones prepare the same karaoke session
                ↓
         Both press Ready
                ↓
          Shared countdown
                ↓
Each phone plays the instrumental locally
and records only its own microphone
                ↓
       Recording finishes
                ↓
      Vocal takes are aligned
                ↓
          Final duet mix
```

Alignment is designed around:

- monotonic start timestamps
- shared sync marker
- waveform cross-correlation
- saved device/output latency profile
- final manual millisecond correction

KaraVox also supports the architecture for **Record Later Duet**, where one singer records first and the second singer completes the duet later.

---

## Current implementation status

The current development branch contains the karaoke-first foundation plus the first production-oriented local processing path.

Implemented so far:

- karaoke domain models and service contracts
- shared online/local preparation coordinator
- online source resolver adapter
- local SAF/content-URI resolver and local metadata reader
- karaoke-first Home/Search/Library/Recordings navigation foundation
- dedicated offline audio picker and online song result flow
- preparation screen for online and local songs
- verified Fast and Balanced UVR MDX model catalog entries with SHA-256 checks
- resumable model download/import manager
- ONNX Runtime MDX runner
- STFT / iSTFT / overlapping chunk inference pipeline
- persistent prepared-stem file cache
- fullscreen karaoke lyric renderer
- Better Lyrics + LRCLIB adapters
- LRC / Enhanced LRC parser
- TTML parser/import with timed spans and hardened XML handling
- manual lyric offset adjustment
- PCM16 WAV microphone recording baseline
- local recording repository and recording list foundation
- recording-first private duet room shell and manual timing correction model
- karaoke preference model and persistent preference keys
- KaraVox-specific settings foundation
- contributor CI for source policy, FOSS unit tests, lint and debug APK build
- full architecture plan in `docs/KARAOKE_ARCHITECTURE.md`

Still in progress before a production release:

- reduce separation peak memory for normal multi-minute songs and complete Android thermal/memory benchmarks
- verify long-session separation on real Android devices
- validate word-level timing quality across real Better Lyrics responses
- tap-to-sync/manual lyric editor and persistent lyric cache/versioning
- AAC/FLAC recording backends and device-capability-aware lossless capture
- countdown/sync-marker generation and automatic detection
- waveform cross-correlation duet alignment
- non-destructive effects/mixer and export pipeline
- duet take exchange / Record Later package transport
- storage quotas and cleanup UI
- complete removal of remaining normal-player/social routes and settings
- accessibility, localization, crash-recovery and release-device verification

---

## Architecture

```text
app/
└─ karaoke/
   ├─ audio/
   ├─ domain/
   ├─ duet/
   ├─ lyrics/
   ├─ model/
   ├─ recording/
   ├─ separator/
   ├─ settings/
   └─ source/
```

The karaoke domain is intentionally separated from Android/UI-specific code so source resolution, separation models, lyrics providers, recorders, and alignment engines can be replaced independently.

See:

- `docs/KARAOKE_ARCHITECTURE.md`

---

## Build

KaraVox is an Android/Kotlin project using Jetpack Compose and Media3.

Typical local build:

```bash
./gradlew assembleFossDebug
```

The pull-request verification workflow runs FOSS unit tests, Android lint and `assembleFossDebug`, then uploads the verified debug APK when successful.

---

## License and upstream attribution

KaraVox is licensed under **GPL-3.0**.

This project is derived from and reuses substantial open-source foundations from **Metrolist**, including parts of its Android media/search/playback/settings infrastructure and bundled integrations. The original Metrolist project and its contributors retain credit for their work.

Important upstream/integrated projects include:

- Metrolist
- InnerTune / OuterTune-derived components
- Ultimate Vocal Remover (UVR) / MDX-Net model ecosystem
- Better Lyrics
- LRCLIB
- AndroidX Media3
- other libraries listed in the repository's dependency files and license notices

KaraVox is developed as its own karaoke-focused product. It is not affiliated with YouTube, Google, Ultimate Vocal Remover, KUIELab, or Metrolist maintainers.

See `LICENSE` and `NOTICE.md` for attribution details.

---
