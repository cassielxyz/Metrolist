#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() {
  echo "::error::$*"
  exit 1
}

note() {
  echo "::notice::$*"
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "Required file is missing: $file"
}

require_text() {
  local file="$1"
  local text="$2"
  local message="$3"
  grep -Fq "$text" "$file" || fail "$message"
}

forbid_text() {
  local file="$1"
  local text="$2"
  local message="$3"
  if grep -Fq "$text" "$file"; then
    fail "$message"
  fi
}

note "Checking KaraVox project identity and required project files"
require_file "README.md"
require_file "NOTICE.md"
require_file "LICENSE"
require_file "docs/KARAOKE_ARCHITECTURE.md"
require_file "app/build.gradle.kts"
require_file "app/src/main/AndroidManifest.xml"
require_file "app/src/main/res/xml/network_security_config.xml"
require_file "app/src/main/kotlin/com/metrolist/music/KaraVoxActivity.kt"
require_file "app/src/main/kotlin/com/metrolist/music/karaoke/separator/KaraVoxModelCatalog.kt"
require_file ".github/workflows/build_pr.yml"
require_file ".github/workflows/release.yml"

require_text "README.md" "# KaraVox" "README must identify the product as KaraVox"
require_text "NOTICE.md" "Metrolist" "NOTICE.md must preserve upstream Metrolist attribution"
require_text "app/build.gradle.kts" 'val baseApplicationId = "com.cassiel.karavox"' "KaraVox application ID changed unexpectedly"
require_text "app/build.gradle.kts" 'appNameOverride ?: "KaraVox"' "KaraVox default app name changed unexpectedly"
require_text "app/src/main/AndroidManifest.xml" 'android:name=".KaraVoxActivity"' "KaraVoxActivity must be the launcher activity"
require_text "app/src/main/res/xml/network_security_config.xml" 'cleartextTrafficPermitted="false"' "Production cleartext network traffic must stay disabled"
forbid_text "app/src/main/AndroidManifest.xml" 'android:name=".MainActivity"' "Legacy Metrolist MainActivity must not be mounted in the production manifest"
forbid_text "app/src/main/AndroidManifest.xml" 'android:name=".playback.MusicService"' "Legacy normal-player MusicService must not be mounted in the production manifest"

note "Checking production release workflow safeguards"
require_text ".github/workflows/release.yml" 'KARAVOX_RELEASE_KEYSTORE_B64' "Release workflow must require a protected KaraVox signing key"
require_text ".github/workflows/release.yml" 'apksigner" verify' "Release workflow must verify the signed APK"
require_text ".github/workflows/release.yml" 'sha256sum dist/KaraVox.apk' "Release workflow must publish an APK checksum"
require_text ".github/workflows/release.yml" 'tags:' "Production release workflow must support tag-triggered releases"

note "Checking for unresolved merge-conflict markers"
if git grep -n -E '^(<<<<<<< |=======|>>>>>>> )' -- . >/tmp/karavox-conflicts.txt 2>/dev/null; then
  cat /tmp/karavox-conflicts.txt
  fail "Unresolved merge-conflict markers were found"
fi

note "Checking whitespace errors"
git diff --check HEAD^ HEAD || fail "The latest commit contains whitespace errors"

note "Checking that large AI model weights are not committed"
model_files="$(git ls-files | grep -E '\.(onnx|ort|pt|pth|ckpt|safetensors)$' || true)"
if [[ -n "$model_files" ]]; then
  echo "$model_files"
  fail "AI model weights must be downloaded/imported at runtime, not committed to Git"
fi

note "Checking that release/private signing material is not committed"
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  case "$file" in
    *debug.keystore|*pr-debug.keystore) ;;
    *) echo "$file"; fail "Private signing material must not be committed" ;;
  esac
done < <(git ls-files | grep -E '(release\.keystore$|\.(jks|p12|pfx|pem)$)' || true)

note "Checking tracked files for obvious private-key/token material"
if git grep -n -E '(-----BEGIN ([A-Z ]+ )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})' -- '*.kt' '*.kts' '*.xml' '*.properties' '*.yml' '*.yaml' '*.toml' '*.json' >/tmp/karavox-secrets.txt 2>/dev/null; then
  cat /tmp/karavox-secrets.txt
  fail "Possible secret/private key found in tracked source"
fi

note "Checking tracked file sizes"
while IFS= read -r -d '' file; do
  [[ -f "$file" ]] || continue
  size=$(wc -c < "$file")
  if (( size > 52428800 )); then
    echo "$file ($size bytes)"
    fail "Tracked files above 50 MiB require explicit maintainer review"
  fi
done < <(git ls-files -z)

note "Checking separator model transport policy"
if grep -nE 'downloadUrl\s*=\s*"http://' app/src/main/kotlin/com/metrolist/music/karaoke/separator/KaraVoxModelCatalog.kt; then
  fail "Separator models must only use HTTPS download URLs"
fi

note "KaraVox source verification passed"
