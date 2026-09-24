# KaraVox release process

KaraVox uses two verification layers: contributor CI for every change and a protected production-signing workflow for published APKs.

## Version policy

- `1.0.0-rc1` is the first release candidate.
- `1.0.0` is published only after automated CI and real-device acceptance checks pass.
- Increment `versionCode` for every production APK that must upgrade an earlier installed production build.

## Required GitHub Actions secrets

The production workflow `.github/workflows/release.yml` requires a permanent Android signing identity. Configure these repository secrets in the independent KaraVox repository:

- `KARAVOX_RELEASE_KEYSTORE_B64` — base64 encoding of the release keystore file
- `KARAVOX_STORE_PASSWORD` — keystore password
- `KARAVOX_KEY_ALIAS` — signing key alias
- `KARAVOX_KEY_PASSWORD` — signing key password

Never commit the keystore, private key, passwords, or their base64 value to Git.

Keep the original keystore and passwords in a secure offline/password-manager backup. Losing the signing key prevents normal updates to APKs signed with that identity.

## Creating a release signing key

One example using the JDK `keytool` is:

```bash
keytool -genkeypair \
  -keystore karavox-release.keystore \
  -alias karavox \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Generate the base64 secret locally without publishing it:

```bash
base64 -w 0 karavox-release.keystore
```

On environments where `base64 -w` is unavailable, use the platform's equivalent single-line base64 command.

## Automated contributor gate

`.github/workflows/build_pr.yml` checks:

1. project identity and required attribution
2. merge-conflict markers and whitespace errors
3. accidental model weights, signing material and obvious token/private-key patterns
4. HTTPS-only separation-model URLs
5. production manifest/runtime invariants
6. unit tests
7. FOSS debug lint
8. FOSS release lint
9. FOSS debug APK assembly
10. minified FOSS release APK assembly
11. SHA-256 generation for produced APKs

A phase is not accepted while this gate is failing.

## Production workflow

`.github/workflows/release.yml` performs the protected release path:

1. checks out the exact release commit/tag with submodules
2. reruns KaraVox source policy
3. validates that all four signing secrets exist
4. verifies the configured keystore/key alias
5. runs unit tests, release lint and release assembly
6. runs Android `zipalign`
7. signs using `apksigner`
8. verifies the resulting APK signature with `apksigner verify`
9. writes `KaraVox.apk.sha256`
10. uploads the signed APK/checksum as an Actions artifact
11. when requested from a release tag/manual publish action, creates or updates the GitHub Release

## Real-device acceptance gate

Before `v1.0.0`, install the RC on at least one supported Android device and verify all of the following with headphones where appropriate:

- app launches into KaraVox, not the legacy music player
- online song search opens karaoke preparation
- local audio picker can prepare a supported song
- Fast model downloads, verifies and separates successfully
- Balanced model downloads, verifies and separates successfully on a capable device
- a multi-minute song completes without out-of-memory/crash
- prepared stems reopen from Library without re-separation
- lyrics display and manual timing adjustment persist
- vocal guide slider stays synchronized during normal practice playback
- microphone permission flow works
- countdown appears before recording
- recorded vocal is playable after app restart
- solo WAV export works
- private duet take export/import works between two installs/devices
- automatic alignment produces a usable result
- manual partner timing adjustment changes the final mix in the expected direction
- final duet WAV export works
- cache/model/prepared-song cleanup works
- airplane/offline use works for already prepared local/cached material that does not require a network lyric/source fetch
- no recording is uploaded automatically

Record the tested device, Android version, model profile, song duration, approximate preparation time, and any thermal/memory observations in the release notes or test log.

## Independent repository

The production release should be made from the independent `cassielxyz/KaraVox` repository rather than a GitHub fork relationship. GPL-3.0 license and upstream attribution in `NOTICE.md` must remain intact after migration.

## Publishing v1.0.0

After RC acceptance:

1. set `versionName = "1.0.0"`
2. keep/increment the correct `versionCode`
3. rerun the full contributor gate
4. merge the verified release commit to `main`
5. create tag `v1.0.0`
6. let the protected production workflow build/sign/verify the APK
7. confirm the published APK checksum matches the workflow artifact
8. install the signed production APK once before announcing the release
