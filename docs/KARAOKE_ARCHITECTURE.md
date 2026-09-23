# KaraVox architecture

KaraVox is a dedicated private karaoke application for Android. The project reuses proven open-source media/search components inherited from Metrolist, but the product, navigation, recording flow and karaoke domain are KaraVox-specific. Upstream copyright and GPL attribution are retained in source history, `LICENSE`, and `NOTICE.md`.

## Product rules

- Karaoke is the product; normal music-player UI is not a primary experience.
- No social feed, followers, public profile, or automatic publishing.
- Online and local songs share one karaoke preparation pipeline.
- Lead-vocal separation is pluggable and local-first.
- Instrumental playback and microphone recording are separate tracks.
- Synced lyrics prefer word timing, with line timing as fallback.
- Remote duet is recording-first, not live microphone streaming.
- A duet can be recorded together or completed later.
- Recordings remain on-device unless the user explicitly shares a duet take/export.
- Inherited GPL code remains under its original license and attribution.

## Target navigation

1. **Home** — online search, local song, private duet, record-later duet.
2. **Search** — song-only online discovery; selection enters karaoke preparation.
3. **Library** — local/offline songs and prepared karaoke cache.
4. **Recordings** — solo takes, duet takes, exports and stems.
5. **Settings** — KaraVox processing, microphone, recording, lyrics, appearance, playback and storage.

## Core preparation pipeline

```text
KaraokeSongRef
      |
      v
KaraokeAudioSourceResolver
  online -> existing InnerTube/playback resolver adapter
  local  -> SAF/content URI adapter
      |
      v
Source cache/fingerprint
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
      +---- private duet recorder
```

`KaraokePreparationCoordinator` depends on interfaces instead of Android UI classes so source resolution, separation models, lyrics providers, recorders and alignment engines can be swapped and tested independently.

## Vocal-separation strategy

KaraVox must only bundle/download model weights with explicit redistribution rights.

### Fast
- Small two-stem MDX/ONNX karaoke model.
- Lowest preparation time and memory use.
- Intended for lower-end devices.

### Balanced
- Higher-quality MDX-family ONNX model after Android benchmark validation.
- Default profile.

### Best
- Higher-quality RoFormer-class model only if memory, thermal behavior, download size and model licensing are acceptable on Android.
- Chunked inference and longer preparation time are acceptable.

Requirements:
- process once and cache stems by source fingerprint + model version
- resumable/cancelable preparation
- never overwrite original audio
- progress reporting
- model SHA-256 verification
- explicit model license/attribution metadata
- safe fallback when the device cannot run a selected model

## Lyrics engine

Internal representation supports:
- line start/end timestamps
- optional word start/end timestamps
- global user offset

Provider priority:
1. Better Lyrics word/synced result
2. LRCLIB synced result
3. local Enhanced LRC/LRC
4. local TTML
5. plain lyrics + manual sync editor

UI behavior:
- current words use the KaraVox/theme highlight color
- future words remain readable
- previous/next lines are dimmed
- independent lyric offset in milliseconds
- manual tap-to-sync/editor fallback
- optional transliteration/translation later

## Recording pipeline

The microphone is always recorded independently from instrumental playback.

Target profiles:

| Profile | Target |
|---|---|
| Standard | AAC, 48 kHz, space-efficient |
| High | high-bitrate AAC, 48 kHz |
| Studio FLAC | lossless 48 kHz capture where device/backend supports it |
| Studio WAV | lossless PCM WAV capture |

The first Android recorder baseline currently implements reliable mono PCM16 WAV capture at the requested sample rate. True 24-bit device-capability handling, AAC and FLAC encoders remain separate backends and must not be advertised as implemented until verified.

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
7. At completion, the vocal take and sync metadata are exchanged with explicit user action.
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

The first singer creates a duet package containing the session manifest and their vocal take. The second singer can complete the other part later; the same post-record alignment pipeline is then used.

## Settings

### Karaoke
- separation quality/model
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
- automatic alignment
- manual offset
- record-later package retention

### Playback / Appearance / Storage
Reuse existing open-source audio/theme/cache foundations where they fit KaraVox, while removing player/social settings that do not belong in the karaoke product.

## Implementation phases

### Phase 1 — KaraVox shell and contracts
- karaoke domain models/contracts
- preparation coordinator
- karaoke-first Home/Search/Recordings
- recording-first private duet room
- word-aware fullscreen lyric component
- karaoke-first bottom navigation
- KaraVox identity/version/README/NOTICE

### Phase 2 — real song and lyric adapters
- online resolver adapter
- local SAF resolver
- local metadata reader
- redirect online/local selection to Prepare Karaoke
- Better Lyrics + LRCLIB adapters
- LRC/Enhanced-LRC parser
- preparation state UI

### Phase 3 — vocal separation
- choose explicitly redistributable ONNX weights
- model manager with download/import, SHA-256 and license metadata
- implement STFT/chunking/inference/iSTFT/overlap-add
- Fast/Balanced/Best profiles
- persistent prepared-stem cache
- Android memory/thermal benchmark gates

### Phase 4 — lyrics completion
- local LRC/Enhanced LRC/TTML import
- verify true word timing from Better Lyrics path
- manual offset + tap-sync editor
- lyrics cache/versioning

### Phase 5 — recording and mixer
- AudioRecord baseline (started)
- AAC/FLAC backends
- device-capability-aware lossless capture
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

### Phase 7 — product cleanup and release
- replace/remove remaining normal-player/social UI paths
- KaraVox-specific Settings
- storage cleanup/cache quotas
- offline tests
- speaker/wired/Bluetooth latency tests
- long-session/thermal/memory tests
- crash recovery during preparation/recording
- accessibility/localization
- complete attribution and GPL source/release workflow

## Acceptance tests

A release candidate is not complete until all of these work:

- Online song -> Prepare Karaoke -> real instrumental -> synced lyrics -> private recording.
- Local MP3/FLAC/WAV/M4A -> same flow without internet after required assets are cached.
- Reopening a prepared song reuses cached stems.
- Missing word lyrics gracefully falls back to line timing.
- Missing synced lyrics can be imported or manually synchronized.
- Mic recording is independent from instrumental and can be remixed.
- Killing/reopening the app does not corrupt existing recordings.
- Together duet remains alignable under deliberately injected network delay.
- Manual +/- millisecond adjustment is audible in preview and preserved in export.
- Record Later duet can be completed in another session/device.
- No recording is uploaded automatically.
