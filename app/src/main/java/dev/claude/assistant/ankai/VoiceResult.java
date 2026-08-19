package dev.claude.assistant.ankai;

/** Ergebnis einer Sprachaufnahme: der sichtbare Chatlauf, den Ankai gestartet hat. */
public final class VoiceResult {

    public final String sessionId;
    public final String runId;
    public final String transcript;

    public VoiceResult(String sessionId, String runId, String transcript) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.transcript = transcript;
    }
}
