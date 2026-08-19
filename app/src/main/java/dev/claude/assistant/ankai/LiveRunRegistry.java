package dev.claude.assistant.ankai;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Prozessweiter, parallel nutzbarer Index der aktuell bekannten Chatlaeufe. */
public final class LiveRunRegistry {

    private final ConcurrentMap<String, LiveRunState> runs = new ConcurrentHashMap<>();

    /**
     * Legt einen Lauf an oder liefert beim Reconnect denselben Zustand wieder.
     * Session-IDs sind serverseitig stabil und deshalb der Registry-Schluessel.
     */
    public LiveRunState start(String sessionId, String runId) {
        String key = requireSessionId(sessionId);
        return runs.computeIfAbsent(key, ignored -> new LiveRunState(key, runId));
    }

    public LiveRunState get(String sessionId) {
        if (sessionId == null) return null;
        return runs.get(sessionId.trim());
    }

    public int size() {
        return runs.size();
    }

    /** Entfernt nur abgeschlossene Laeufe; aktive Hintergrundarbeit bleibt erhalten. */
    public boolean removeFinished(String sessionId) {
        LiveRunState state = get(sessionId);
        return state != null && state.isDone() && runs.remove(state.sessionId(), state);
    }

    private static String requireSessionId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Session-ID fehlt");
        }
        return value.trim();
    }
}
