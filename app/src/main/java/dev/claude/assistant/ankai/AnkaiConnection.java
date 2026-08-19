package dev.claude.assistant.ankai;

/**
 * Gespeicherte Verknuepfung zu einer Ankai-Instanz.
 *
 * Enthaelt Zugangsdaten und darf deshalb niemals geloggt werden;
 * {@link #toString()} blendet Passwort und Sessioncookie aus.
 */
public final class AnkaiConnection {

    public final String baseUrl;
    public final String username;
    public final String password;
    public final String defaultProjectId;
    public final String defaultProjectName;
    public final String sessionCookie;

    public AnkaiConnection(String rawUrl, String username, String password) {
        this(rawUrl, username, password, null, null, null);
    }

    public AnkaiConnection(String rawUrl, String username, String password,
                           String defaultProjectId, String defaultProjectName, String sessionCookie) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Benutzername fehlt");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Passwort fehlt");
        }
        this.baseUrl = new AnkaiEndpoint(rawUrl).baseUrl();
        this.username = username.trim();
        this.password = password;
        this.defaultProjectId = emptyToNull(defaultProjectId);
        this.defaultProjectName = emptyToNull(defaultProjectName);
        this.sessionCookie = emptyToNull(sessionCookie);
    }

    public AnkaiEndpoint endpoint() {
        return new AnkaiEndpoint(baseUrl);
    }

    /** Client fuer diese Verknuepfung; ein vorhandenes Cookie wird wiederverwendet. */
    public AnkaiClient newClient() {
        AnkaiClient client = new AnkaiClient(endpoint(), username, password);
        if (sessionCookie != null) client.useSessionCookie(sessionCookie);
        return client;
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return "AnkaiConnection{" + baseUrl + ", user=" + username
                + ", defaultProject=" + defaultProjectId + "}";
    }
}
