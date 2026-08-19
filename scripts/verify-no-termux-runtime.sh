#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
JAVA_ROOT="$ROOT/app/src/main/java"

if grep -q 'com\.termux\|TermuxResultReceiver' "$MANIFEST"; then
  echo "AndroidManifest.xml enthält weiterhin Termux-Laufzeitintegration" >&2
  exit 1
fi

if grep -R -n -E 'com\.termux|TermuxBridge|TermuxResultReceiver' "$JAVA_ROOT"; then
  echo "App-Quellen enthalten weiterhin Termux-Laufzeitintegration" >&2
  exit 1
fi

echo "Keine Termux-Laufzeitintegration in Manifest oder App-Quellen"
