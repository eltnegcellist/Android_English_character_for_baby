#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/libs"
OUT="$OUT_DIR/moonshine-voice-0.1.5-no-ort.aar"
URL="https://repo1.maven.org/maven2/ai/moonshine/moonshine-voice/0.1.5/moonshine-voice-0.1.5.aar"

mkdir -p "$OUT_DIR"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SOURCE="$WORK/moonshine-voice-0.1.5.aar"
CHECKSUM="$WORK/moonshine-voice-0.1.5.aar.sha1"

curl -fL --retry 3 --retry-delay 2 "$URL" -o "$SOURCE"
curl -fL --retry 3 --retry-delay 2 "$URL.sha1" -o "$CHECKSUM"
EXPECTED="$(tr -d '[:space:]' < "$CHECKSUM")"
echo "$EXPECTED  $SOURCE" | sha1sum --check

mkdir "$WORK/unpacked"
(
  cd "$WORK/unpacked"
  unzip -q "$SOURCE"
)

count="$(find "$WORK/unpacked/jni" -type f -name 'libonnxruntime.so' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$count" -lt 1 ]; then
  echo "Expected Moonshine AAR to contain libonnxruntime.so." >&2
  exit 1
fi

find "$WORK/unpacked/jni" -type f -name 'libonnxruntime.so' -delete

if find "$WORK/unpacked/jni" -type f -name 'libonnxruntime.so' | grep -q .; then
  echo "Failed to remove Moonshine's reduced ONNX Runtime." >&2
  exit 1
fi

rm -f "$OUT"
(
  cd "$WORK/unpacked"
  zip -q -r "$OUT" .
)

unzip -l "$OUT" | grep -q 'libmoonshine'
if unzip -l "$OUT" | grep -q 'libonnxruntime.so'; then
  echo "Stripped Moonshine AAR still contains libonnxruntime.so." >&2
  exit 1
fi

echo "Prepared $OUT without Moonshine's reduced libonnxruntime.so."
