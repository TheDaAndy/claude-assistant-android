package dev.claude.assistant.ankai;

/** Unveraenderliche UI-Sicht auf einen laufenden Ankaï-Chat. */
public final class LiveRunSnapshot {
    private final String sessionId;
    private final String runId;
    private final String text;
    private final boolean done;
    private final boolean speechAllowed;

    LiveRunSnapshot(String sessionId, String runId, String text, boolean done,
                    boolean speechAllowed) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.text = text;
        this.done = done;
        this.speechAllowed = speechAllowed;
    }

    public String sessionId() { return sessionId; }
    public String runId() { return runId; }
    public String text() { return text; }
    public boolean isDone() { return done; }
    public boolean maySpeak() { return speechAllowed; }
}
