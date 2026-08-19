package dev.claude.assistant.ankai;

import java.io.IOException;

/**
 * Verbindet den prozessweiten Laufzustand mit seiner verschluesselten Ablage.
 *
 * Android-Services duerfen verschwinden und neu entstehen, ohne aktive Sessions
 * zu verlieren. Nur ein bestaetigtes Ende entfernt den Reconnect-Eintrag;
 * transportbedingte Fehler bleiben absichtlich fuer einen spaeteren Versuch stehen.
 */
public final class LiveRunCoordinator {
    private final ActiveRunStore store;
    private final LiveRunRegistry registry;

    public LiveRunCoordinator(ActiveRunStore store) {
        if (store == null) throw new IllegalArgumentException("Laufablage fehlt");
        this.store = store;
        this.registry = store.restoreRegistry();
    }

    /** Registriert den von /api/voice bestaetigten Lauf vor dem ersten /live-Aufruf. */
    public LiveRunState track(VoiceResult result) {
        if (result == null) throw new IllegalArgumentException("Voice-Ergebnis fehlt");
        store.remember(result.sessionId, result.runId);
        return registry.start(result.sessionId, result.runId);
    }

    public LiveRunRegistry registry() {
        return registry;
    }

    /**
     * Verbindet genau eine Session. Bei done oder serverseitig inaktivem Lauf
     * ist kein weiterer Reconnect noetig. IOException belaesst die Persistenz.
     */
    public boolean reconnect(String sessionId, LiveStream stream) throws IOException {
        return reconnect(sessionId, null, stream);
    }

    public boolean reconnect(String sessionId, HistoryLoader history, LiveStream stream) throws IOException {
        if (stream == null) throw new IllegalArgumentException("Live-Stream fehlt");
        LiveRunState state = registry.get(sessionId);
        if (state == null) throw new IllegalArgumentException("Unbekannte Session-ID");

        if (history != null && state.text().isEmpty()) {
            state.restoreHistory(history.load(state.sessionId()));
        }

        boolean active = stream.open(state.sessionId(), event -> {
            state.accept(event);
            if (state.isDone()) store.forget(state.sessionId());
        });
        if (!active || state.isDone()) store.forget(state.sessionId());
        return active;
    }

    @FunctionalInterface
    public interface LiveStream {
        boolean open(String sessionId, LiveRunListener listener) throws IOException;
    }

    @FunctionalInterface
    public interface HistoryLoader {
        java.util.List<String> load(String sessionId) throws IOException;
    }
}
