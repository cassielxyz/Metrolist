# Karaoke-first architecture

This fork turns Metrolist into a dedicated private karaoke application. Metrolist remains the media/search/settings foundation, while the user-facing product becomes karaoke-first.

## Product rules

- No social feed, followers, public profile, or automatic publishing.
- Online and local songs share one karaoke preparation pipeline.
- Lead-vocal separation is pluggable and local-first.
- Instrumental playback and microphone recording are separate tracks.
- Synced lyrics prefer word timing, with line timing as fallback.
- Remote duet is recording-first, not live microphone streaming.
- A duet can be recorded together or completed later.
- Recordings remain on-device unless the user explicitly shares a duet take/export.
- Modified Metrolist code remains subject to GPL-3.0 obligations.

## Target navigation

1. **Home** — online search, local song, private duet, record-later duet.
2. **Search** — Metrolist online discovery adapted so song selection enters karaoke preparation.
3. **Library** — local/offline songs and prepared karaoke cache.
4. **Recordings** — solo takes, duet takes, exports and stems.
5. **Settings** — existing Metrolist settings shell with karaoke-specific sections.

The normal music-player experience is not a primary destination.

## Core preparation pipeline

```text
KaraokeSongRef
      |
      v
KaraokeAudioSourceResolver
  online -> existing Metrolist resolver/cache
  local  -> MediaStore/SAF adapter
      |
      v
VocalSeparator
  FAST / BALANCED / BEST
      |
      +---- vocals stem
      +---- instrumental stem
      |
      v
Lyrics providers
  Better Lyrics -> LRCLIB -> local LRC/Enhanced LRC/TTML -> manual sync
      |
      v
KaraokeSession
      |
      +---- fullscreen lyrics player
      +---- practice vocal mix
      +---- solo recorder
      +---- duet recorder
```

`KaraokePreparationCoordinator` intentionally depends on interfaces only. This keeps Metrolist-specific source resolution, ONNX/native separation, lyrics services and recording implementations replaceable and testable.

## Stem separation plan

### Fast
- Mobile-optimized two-stem ONNX model.
- Lowest memory and preparation time.
- Suitable for lower-end devices.

### Balanced
- Higher-quality two-stem MDX-family/RoFormer-compatible ONNX model after Android benchmarking.
- Default profile.

### Best
- Highest-quality model that passes memory/thermal tests on supported devices.
- May use chunked inference and a longer preparation time.

Requirements:
- Process once, cache stems by source fingerprint + model version.
- Cancel safely when the user leaves preparation.
- Never overwrite the original song.
- Expose progress to UI.
- Validate model licenses before bundling/distribution.

## Lyrics engine

Supported internal representation:

- line start/end timestamps
- optional word start/end timestamps
- global user offset

Provider priority:

1. Better Lyrics word-synced result
2. LRCLIB synced result
3. local Enhanced LRC/LRC
4. local TTML
5. plain lyrics + manual sync editor

UI behavior:
- current words use the theme highlight color
- future words remain white
- previous/next lines are dimmed
- independent lyric offset in milliseconds
- manual tap-to-sync/editor is retained as fallback

## Recording pipeline

The microphone is always recorded independently from the instrumental.

Profiles:

| Profile | Target |
|---|---|
| Standard | AAC, 48 kHz, space-efficient |
| High | high-bitrate AAC, 48 kHz |
| Studio FLAC | 48 kHz / 24-bit capture path where device support permits |
| Studio WAV | 48 kHz / 24-bit capture path where device support permits |

Post-processing is non-destructive:
- noise reduction when supported/enabled
- echo cancellation when appropriate
- compressor
- normalization
- vocal/instrumental gain
- optional reverb

Exports:
- final mix
- singer A vocal
- singer B vocal
- instrumental
- optional all-stems package

## Private duet protocol

### Together recording

1. Host selects/prepares a karaoke session.
2. Session manifest shares song identity, source fingerprint, lyric revision, key, tempo and expected duration.
3. Partner verifies/prepares the same session.
4. Both clients enter Ready state.
5. Coordinated countdown emits a detectable sync marker.
6. Each device plays the instrumental locally and records only its microphone.
7. At completion, the vocal take and sync metadata are exchanged with explicit user consent.
8. Alignment engine calculates the partner offset.
9. User can apply a final manual millisecond trim.
10. Mixer renders preview/export.

### Alignment order

1. monotonic start timestamps for coarse alignment
2. detected sync marker for deterministic correction
3. waveform cross-correlation for fine correction
4. saved device/output latency profile
5. manual offset as final override

The network never needs to keep two live microphone streams phase-aligned.

### Record later

The first singer creates a duet package containing the session manifest and their vocal take. The second singer can complete the other part later; the same post-recording alignment pipeline is then used.

## Karaoke settings

### Karaoke
- separation quality
- default practice-vocal mix
- countdown
- lyrics style/offset
- word highlighting
- key/tempo defaults

### Microphone
- input device
- mic gain
- monitoring
- noise suppression
- echo cancellation
- compressor
- saved Bluetooth/wired latency profiles

### Recording
- Standard / High / Studio FLAC / Studio WAV
- save individual stems
- auto-mix duet

### Duets
- recording-together defaults
- automatic alignment
- manual offset
- record-later package retention

### Playback / Appearance / Storage
Reuse Metrolist infrastructure where it matches the karaoke product, including equalizer, normalization, cache, themes and storage management.

## Implementation phases

### Phase 1 — karaoke shell and contracts
- karaoke domain models/contracts
- preparation coordinator
- karaoke-first Home
- Recordings destination
- recording-first private duet room
- word-aware fullscreen lyric component
- karaoke-first bottom navigation

### Phase 2 — real song adapters
- wrap existing Metrolist online resolver/cache as `KaraokeAudioSourceResolver`
- local MediaStore/SAF resolver
- redirect online/local song selection to Prepare Karaoke instead of normal player
- preparation progress screen and persistent prepared-song cache

### Phase 3 — vocal separation
- benchmark candidate ONNX models on Android
- implement chunked inference, progress, cancellation and model versioning
- Fast/Balanced/Best selection
- validate output and cache reuse

### Phase 4 — lyrics
- Better Lyrics adapter
- LRCLIB adapter
- local LRC/Enhanced LRC/TTML parser
- word timing renderer integration
- manual offset + tap-sync editor

### Phase 5 — recording and mixer
- AudioRecord/AAudio-compatible capture implementation
- quality profiles
- independent vocal files
- countdown/sync marker
- effects chain and non-destructive mixer
- local recording database and export

### Phase 6 — private duet
- room/session manifest
- Ready/start coordination
- explicit take exchange
- sync-marker detection
- waveform cross-correlation
- manual millisecond trim
- Record Later duet package

### Phase 7 — hardening and release
- replace/remove remaining normal-player UI paths
- karaoke-specific Settings UI
- storage cleanup and cache quotas
- offline tests
- device latency tests (speaker/wired/Bluetooth)
- long-session/thermal/memory tests
- crash recovery during preparation/recording
- accessibility/localization
- license attribution and GPL source/release workflow

## Acceptance tests

A release candidate is not complete until all of these work:

- Online song -> Prepare Karaoke -> instrumental -> synced lyrics -> private recording.
- Local MP3/FLAC/WAV/M4A -> same flow without internet after required assets are cached.
- Reopening a prepared song reuses cached stems.
- Missing word lyrics gracefully falls back to line timing.
- Missing synced lyrics can be manually offset/synced.
- Mic recording is independent from instrumental and can be remixed.
- Killing/reopening the app does not corrupt existing recordings.
- Together duet remains alignable under deliberately injected network delay.
- Manual +/- millisecond adjustment is audible in preview and preserved in export.
- Record Later duet can be completed on a separate session/device.
- No recording is uploaded without explicit user action.
