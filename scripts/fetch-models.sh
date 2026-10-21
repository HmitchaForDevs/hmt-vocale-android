#!/usr/bin/env bash
# Downloads the free Vosk offline models (Apache 2.0) into the app assets.
set -euo pipefail
cd "$(dirname "$0")/.."
A=app/src/main/assets
mkdir -p "$A" /tmp/vosk
fetch() { # name url-zip
  local name=$1 zip=$2
  [ -f "$A/$name/uuid" ] && return 0
  curl -fsSL --retry 3 -o /tmp/vosk/$name.zip "https://alphacephei.com/vosk/models/$zip.zip"
  rm -rf "/tmp/vosk/$zip" "$A/$name"
  unzip -q /tmp/vosk/$name.zip -d /tmp/vosk
  mv "/tmp/vosk/$zip" "$A/$name"
  echo "$zip" > "$A/$name/uuid"
  echo "Bundled $zip as $name"
}
fetch model-fr vosk-model-small-fr-0.22
fetch model-en vosk-model-small-en-us-0.15
du -sh "$A"/*
