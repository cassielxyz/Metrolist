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
- TTML support planned in the karaoke adapter layer
- Word-by-word highlighting when timing is available
- Line-level fallback when word timing is unavailable
- Manual lyric offset and sync correction

### Recording

- Separate microphone recording from instrumental playback
- Standard / High / Studio recording profiles
- AAC / FLAC / WAV targets
- Optional noise suppression
- Optional echo cancellation
- Compressor and normalization pipeline
- Non-destructive mixing after recording

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

The current development branch is building the karaoke-first foundation.

Implemented so far:

- karaoke domain models and service contracts
- shared online/local preparation coordinator
- online source resolver adapter
- local source resolver adapter
- karaoke-first Home screen
- dedicated Recordings destination
- dedicated offline audio picker
- karaoke-only online song result flow
- preparation screen for online and local songs
- fullscreen karaoke lyric renderer
- word-aware lyric model
- LRC / Enhanced LRC parser
- Better Lyrics adapter
- LRCLIB adapter
- recording-first private duet room shell
- manual duet timing correction control
- karaoke preference model and persistent preference keys
- karaoke/recording settings screen foundation
- full architecture plan in `docs/KARAOKE_ARCHITECTURE.md`

Still in progress:

- production mobile vocal-separation engine
- prepared-stem persistent cache implementation
- metadata handoff from search to preparation
- complete TTML karaoke mapping
- microphone recording backend
- FLAC/WAV encoder path
- duet take exchange
- automatic sync-marker detection
- waveform cross-correlation alignment
- post-record mixer/export
- complete removal of remaining normal-player-oriented routes and settings

---

## Architecture

```text
app/
└─ karaoke/
   ├─ model/
   ├─ domain/
   ├─ source/
   ├─ separator/
   ├─ lyrics/
   ├─ recording/
   ├─ duet/
   ├─ cache/
   └─ settings/
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

The project CI builds and lints the FOSS debug variant for pull requests.

---

## License and upstream attribution

KaraVox is licensed under **GPL-3.0**.

This project is derived from and reuses substantial open-source foundations from **Metrolist**, including parts of its Android media/search/playback/settings infrastructure and bundled integrations. The original Metrolist project and its contributors retain credit for their work.

Important upstream/integrated projects include:

- Metrolist
- InnerTune / OuterTune-derived components
- Better Lyrics
- LRCLIB
- AndroidX Media3
- other libraries listed in the repository's dependency files and license notices

KaraVox is developed as its own karaoke-focused product. It is not affiliated with YouTube or Google.

See `LICENSE` and `NOTICE.md` for attribution details.

---

## Project direction

The final product should feel like this:

```text
KaraVox
├─ Home
├─ Search
├─ Library
├─ Recordings
└─ Settings

Song
 ↓
Prepare Karaoke
 ↓
Instrumental + Synced Lyrics
 ↓
Solo / Private Duet / Record Later
 ↓
Mix + Export
```

No normal music-player-first experience. Karaoke is the product.
