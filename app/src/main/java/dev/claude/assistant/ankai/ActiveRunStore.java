package dev.claude.assistant.ankai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prozessfeste Ablage der noch zu reconnectenden Sessions.
 *
 * Die Klasse bleibt frei von android.*. Auf dem Geraet wird ihr SecretStore
 * durch EncryptedSharedPreferences bereitgestellt. Pro Zeile werden Session-
 * und Run-ID Base64-kodiert, damit Trennzeichen in serverseitigen IDs keinen
 * zweiten oder falschen Eintrag erzeugen koennen.
 */
public final class ActiveRunStore {
    static final String STORAGE_KEY = "ankai.active_runs.v1";

    private final SecretStore storage;

    public ActiveRunStore(SecretStore storage) {
        if (storage == null) throw new IllegalArgumentException("Ablage fehlt");
        this.storage = storage;
    }

    public synchronized void remember(String sessionId, String runId) {
        String session = requireSessionId(sessionId);
        LinkedHashMap<String, ActiveRun> runs = readBySession();
        runs.put(session, new ActiveRun(session, blankToNull(runId)));
        write(runs);
    }

    public synchronized boolean forget(String sessionId) {
        String session = blankToNull(sessionId);
        if (session == null) return false;
        LinkedHashMap<String, ActiveRun> runs = readBySession();
        boolean removed = runs.remove(session) != null;
        if (removed) write(runs);
        return removed;
    }

    public synchronized List<ActiveRun> load() {
        return new ArrayList<>(readBySession().values());
    }

    /** Baut nach einem Prozessneustart den lokalen Index fuer /live-Reconnect auf. */
    public synchronized LiveRunRegistry restoreRegistry() {
        LiveRunRegistry registry = new LiveRunRegistry();
        for (ActiveRun run : readBySession().values()) {
            registry.start(run.sessionId(), run.runId());
        }
        return registry;
    }

    private LinkedHashMap<String, ActiveRun> readBySession() {
        LinkedHashMap<String, ActiveRun> runs = new LinkedHashMap<>();
        String encoded = storage.get(STORAGE_KEY);
        if (encoded == null || encoded.trim().isEmpty()) return runs;
        for (String line : encoded.split("\\n")) {
            String[] fields = line.split("\\t", -1);
            if (fields.length != 2) continue;
            try {
                String sessionId = blankToNull(decode(fields[0]));
                String runId = blankToNull(decode(fields[1]));
                if (sessionId != null) runs.put(sessionId, new ActiveRun(sessionId, runId));
            } catch (IllegalArgumentException ignored) {
                // Ein beschaedigter Datensatz darf andere reconnectbare Laeufe nicht verlieren.
            }
        }
        return runs;
    }

    private void write(Map<String, ActiveRun> runs) {
        if (runs.isEmpty()) {
            storage.remove(STORAGE_KEY);
            return;
        }
        StringBuilder value = new StringBuilder();
        for (ActiveRun run : runs.values()) {
            if (value.length() > 0) value.append('\n');
            value.append(encode(run.sessionId())).append('\t');
            value.append(encode(run.runId() == null ? "" : run.runId()));
        }
        storage.put(STORAGE_KEY, value.toString());
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String requireSessionId(String value) {
        String sessionId = blankToNull(value);
        if (sessionId == null) throw new IllegalArgumentException("Session-ID fehlt");
        return sessionId;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
