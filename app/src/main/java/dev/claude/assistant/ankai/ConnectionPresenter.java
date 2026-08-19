package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Android-freie Verknuepfungslogik fuer MainActivity.
 *
 * Speichert Zugangsdaten erst, nachdem Anmeldung und Projektabruf erfolgreich waren.
 * Dadurch bleibt nach Teilfehlern keine scheinbar gueltige Verknuepfung zurueck.
 */
public final class ConnectionPresenter {
    private final AnkaiConnectionStore store;
    private final AnkaiGatewayFactory gatewayFactory;
    private ConnectionUiState state = ConnectionUiState.disconnected(null);
    private AnkaiGateway gateway;

    public ConnectionPresenter(AnkaiConnectionStore store) {
        this(store, AnkaiClientGateway::new);
    }

    public ConnectionPresenter(AnkaiConnectionStore store, AnkaiGatewayFactory gatewayFactory) {
        this.store = store;
        this.gatewayFactory = gatewayFactory;
    }

    public ConnectionUiState state() {
        return state;
    }

    /** Laedt und prueft eine bestehende Verknuepfung fuer die initiale Anzeige. */
    public ConnectionUiState refresh() {
        AnkaiConnection connection = store.load();
        if (connection == null) {
            gateway = null;
            state = ConnectionUiState.disconnected(null);
            return state;
        }
        gateway = gatewayFactory.create(connection);
        try {
            gateway.verifyConnection();
            List<AnkaiProject> projects = gateway.listProjects();
            store.saveSessionCookie(gateway.sessionCookie());
            state = connected(connection, projects, null);
        } catch (IOException error) {
            state = connected(connection, Collections.emptyList(), message(error));
        }
        return state;
    }

    /** Prueft Zugangsdaten und speichert sie nur nach vollstaendig erfolgreicher Initialisierung. */
    public ConnectionUiState connect(String rawUrl, String username, String password) {
        final AnkaiConnection candidate;
        try {
            candidate = new AnkaiConnection(rawUrl, username, password);
        } catch (IllegalArgumentException invalid) {
            state = ConnectionUiState.disconnected(message(invalid));
            return state;
        }

        AnkaiGateway candidateGateway = gatewayFactory.create(candidate);
        try {
            String verifiedUser = candidateGateway.verifyConnection();
            List<AnkaiProject> projects = candidateGateway.listProjects();
            AnkaiConnection verified = new AnkaiConnection(candidate.baseUrl, verifiedUser, candidate.password,
                    null, null, candidateGateway.sessionCookie());
            store.save(verified);
            gateway = candidateGateway;
            state = connected(verified, projects, null);
        } catch (IOException error) {
            state = ConnectionUiState.disconnected(message(error));
        }
        return state;
    }

    /** Waehlt ein Projekt aus der geladenen Serverliste oder entfernt den Default mit null. */
    public ConnectionUiState selectDefaultProject(String projectId) {
        AnkaiConnection connection = store.load();
        if (connection == null) {
            state = ConnectionUiState.disconnected("Keine Ankai-Verknuepfung vorhanden");
            return state;
        }
        AnkaiProject selected = null;
        if (projectId != null && !projectId.trim().isEmpty()) {
            for (AnkaiProject project : state.projects) {
                if (projectId.equals(project.id)) {
                    selected = project;
                    break;
                }
            }
            if (selected == null) {
                state = connected(connection, state.projects, "Projekt ist nicht verfügbar");
                return state;
            }
        }
        store.saveDefaultProject(selected);
        AnkaiConnection updated = store.load();
        state = connected(updated, state.projects, null);
        return state;
    }

    /** Trennt best effort auf dem Server und immer lokal. */
    public ConnectionUiState disconnect() {
        try {
            if (gateway != null) gateway.disconnect();
        } catch (IOException ignored) {
            // Die lokale Trennung darf nicht an einem nicht erreichbaren Server scheitern.
        } finally {
            store.clear();
            gateway = null;
            state = ConnectionUiState.disconnected(null);
        }
        return state;
    }

    private static ConnectionUiState connected(AnkaiConnection connection, List<AnkaiProject> projects,
                                                String error) {
        return new ConnectionUiState(true, false, connection.baseUrl, connection.username,
                connection.defaultProjectId, projects, error);
    }

    private static String message(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? "Ankai-Verbindung fehlgeschlagen" : value;
    }
}
