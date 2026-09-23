# Dedicated Karaoke Architecture

This fork uses Metrolist as infrastructure, not as the product UI. The final application is a private karaoke recorder for online and local songs.

## Product navigation

`Home -> Search -> Library -> Rooms/Recordings -> Settings`

Selecting a song enters **Prepare Karaoke**, never the normal music Now Playing UI.

## Reused Metrolist infrastructure

Keep the existing online song discovery/resolution, local media access, Media3 playback/cache, metadata/artwork, BetterLyrics/LRCLIB integrations, theme primitives, DataStore preferences, equalizer/audio normalization where compatible, and useful download/cache management.

Normal album/artist/player/radio/listen-together UI is not part of the final navigation. Existing Listen Together code may be mined for room transport/state, but live microphone streaming is explicitly out of scope.

## Karaoke pipeline

1. Resolve an online or local source.
2. Materialize/cache audio when separation requires seekable input.
3. Reuse a cached separation result when the source/model fingerprint matches.
4. Run a two-stem separator: instrumental + vocals. Implement behind `VocalSeparator` so models can change without UI changes.
5. Resolve lyrics: word-synced provider -> line-synced provider -> local LRC/TTML -> manual timing.
6. Start a local playback/recording session.
7. Record microphone independently from the instrumental.
8. Align duet recordings after capture using a sync marker and bounded waveform correlation; retain manual +/- millisecond correction.
9. Mix non-destructively and export final mix and optional stems.

## Recording modes

- **Solo**: local instrumental + local microphone recording.
- **Duet Together**: participants prepare the same song/settings and record independent vocals. Network room state coordinates readiness; it does not carry live voice.
- **Duet Async**: first singer records and creates a room/session package; partner records later against the same instrumental/timeline.

Recommended studio capture is 48 kHz / 24-bit PCM with FLAC archival/export where supported. Lower-quality AAC presets are optional. Keep raw vocal takes until the user deletes them.

## Synchronization

Every take stores the monotonic recording start, source position, audio-device metadata, sync-marker position and correction values. Automatic alignment is post-recording. Manual correction is independent per participant and lyrics have a separate display offset.

Do not claim sample-perfect sync from wall-clock/network timestamps alone.

## Karaoke settings

- Separation quality: Fast / Balanced / Best
- Vocal guide level
- Countdown and sync marker
- Lyrics provider/style/offset and word highlighting
- Microphone source/gain/monitoring/noise processing
- Recording preset and stem retention
- Automatic/manual duet alignment
- Default key and tempo
- Theme/dynamic color/AMOLED
- Cache, separated stems and recording storage

## Module direction

`karaoke/model` session/domain types

`karaoke/separation` separator interface + model implementations

`karaoke/lyrics` normalized timed-word model and provider adapters

`karaoke/recording` AudioRecord capture, session manifests and quality presets

`karaoke/sync` marker detection/correlation/manual correction

`karaoke/mix` non-destructive mix graph and export

`karaoke/room` private readiness/session metadata transport

`karaoke/ui` dedicated Compose screens

## Privacy

Recordings and stems are local by default. A room transfers only what the selected duet workflow requires. No public profile/feed/follower system is needed. Export/share is always explicit.

## Delivery phases

1. Foundation/domain + recording alignment.
2. Dedicated navigation and Prepare Karaoke UI.
3. Local recording engine and session persistence.
4. Timed lyrics renderer/adapters.
5. Vocal-separation backend and cache.
6. Private recording rooms + async duet package.
7. Mixer/export and device latency calibration.
8. Remove/dead-strip legacy normal-player surfaces after migration and run Android build/device tests.
