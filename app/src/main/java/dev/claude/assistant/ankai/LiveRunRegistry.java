package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Prozessweiter, parallel nutzbarer Index der aktuell bekannten Chatlaeufe. */
public final class LiveRunRegistry {

    private final ConcurrentMap<String, LiveRunState> runs = new ConcurrentHashMap<>();
    private final AtomicReference<LiveRunState> latest = new AtomicReference<>();
    private final List<LiveRunRegistryObserver> latestObservers = new CopyOnWriteArrayList<>();

    /**
     * Legt einen Lauf an oder liefert beim Reconnect denselben Zustand wieder.
     * Session-IDs sind serverseitig stabil und deshalb der Registry-Schluessel.
     */
    public LiveRunState start(String sessionId, String runId) {
        String key = requireSessionId(sessionId);
        LiveRunState existing = runs.get(key);
        if (existing != null) return existing;
        LiveRunState created = new LiveRunState(key, runId);
        LiveRunState raced = runs.putIfAbsent(key, created);
        if (raced != null) return raced;
        latest.set(created);
        for (LiveRunRegistryObserver observer : latestObservers) observer.onLatestRun(created);
        return created;
    }

    /** Zuletzt erstmals registrierter Lauf, geeignet als Standard fuer ein neues Overlay. */
    public LiveRunState latest() {
        return latest.get();
    }

    /** Meldet neue Laeufe und sofort den aktuellsten Zustand, falls vorhanden. */
    public LiveRunSubscription observeLatest(LiveRunRegistryObserver observer) {
        if (observer == null) throw new IllegalArgumentException("Observer fehlt");
        latestObservers.add(observer);
        LiveRunState current = latest.get();
        if (current != null) observer.onLatestRun(current);
        return () -> latestObservers.remove(observer);
    }

    public LiveRunState get(String sessionId) {
        if (sessionId == null) return null;
        return runs.get(sessionId.trim());
    }

    public int size() {
        return runs.size();
    }

    /** Stabile Momentaufnahme fuer parallele Hintergrund-Reconnects. */
    public List<LiveRunState> snapshot() {
        return new ArrayList<>(runs.values());
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
