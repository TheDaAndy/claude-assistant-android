#!/usr/bin/env bash
# Fuehrt die abhaengigkeitsfreien JVM-Tests der Ankai-Netzwerkschicht aus.
# Benoetigt nur ein JDK 17, kein Android-SDK.
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build/jvm-tests"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT"/app/src/main/java/dev/claude/assistant/ankai/*.java \
  "$ROOT"/jvm-tests/TestRunner.java \
  "$ROOT"/jvm-tests/dev/claude/assistant/ankai/*.java

java -cp "$OUT" TestRunner \
  dev.claude.assistant.ankai.AnkaiJsonTest \
  dev.claude.assistant.ankai.AnkaiEndpointTest \
  dev.claude.assistant.ankai.VoiceRequestTest \
  dev.claude.assistant.ankai.AnkaiClientTest \
  dev.claude.assistant.ankai.VoiceSubmissionTest \
  dev.claude.assistant.ankai.VoiceUiFormatterTest \
  dev.claude.assistant.ankai.LiveRunRegistryTest \
  dev.claude.assistant.ankai.LiveRunCoordinatorTest \
  dev.claude.assistant.ankai.LiveRunReconnectLoopTest \
  dev.claude.assistant.ankai.ActiveRunStoreTest \
  dev.claude.assistant.ankai.PlaybackSettingsTest \
  dev.claude.assistant.ankai.AnkaiConnectionStoreTest \
  dev.claude.assistant.ankai.ConnectionPresenterTest
