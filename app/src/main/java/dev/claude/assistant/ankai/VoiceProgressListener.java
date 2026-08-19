package dev.claude.assistant.ankai;

/** Fortschrittsmeldungen des NDJSON-Streams von POST /api/voice. */
public interface VoiceProgressListener {
    void onProgress(int percent, String stage);
}
