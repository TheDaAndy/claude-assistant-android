package dev.claude.assistant.ankai;

/** Persistierbare minimale Identitaet eines noch laufenden Ankai-Chats. */
public final class ActiveRun {
    private final String sessionId;
    private final String runId;

    ActiveRun(String sessionId, String runId) {
        this.sessionId = sessionId;
        this.runId = runId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String runId() {
        return runId;
    }
}
