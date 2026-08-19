package dev.claude.assistant.ankai;

/**
 * Persistiert die Ankai-Verknuepfung in einer {@link SecretStore}-Ablage.
 *
 * Zugangsdaten und Sessioncookie landen ausschliesslich dort und werden
 * nirgends geloggt.
 */
public final class AnkaiConnectionStore {

    static final String KEY_BASE_URL = "ankai.baseUrl";
    static final String KEY_USERNAME = "ankai.username";
    static final String KEY_PASSWORD = "ankai.password";
    static final String KEY_PROJECT_ID = "ankai.defaultProjectId";
    static final String KEY_PROJECT_NAME = "ankai.defaultProjectName";
    static final String KEY_SESSION_COOKIE = "ankai.sessionCookie";

    private final SecretStore secrets;

    public AnkaiConnectionStore(SecretStore secrets) {
        this.secrets = secrets;
    }

    /** Liefert die gespeicherte Verknuepfung oder null, wenn keine vollstaendige vorliegt. */
    public AnkaiConnection load() {
        String baseUrl = secrets.get(KEY_BASE_URL);
        String username = secrets.get(KEY_USERNAME);
        String password = secrets.get(KEY_PASSWORD);
        if (isBlank(baseUrl) || isBlank(username) || isBlank(password)) return null;
        try {
            return new AnkaiConnection(baseUrl, username, password,
                    secrets.get(KEY_PROJECT_ID), secrets.get(KEY_PROJECT_NAME),
                    secrets.get(KEY_SESSION_COOKIE));
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    public boolean isConnected() {
        return load() != null;
    }

    /** Speichert eine neue Verknuepfung; Default-Projekt und Cookie der alten werden verworfen. */
    public void save(AnkaiConnection connection) {
        clear();
        secrets.put(KEY_BASE_URL, connection.baseUrl);
        secrets.put(KEY_USERNAME, connection.username);
        secrets.put(KEY_PASSWORD, connection.password);
        if (connection.defaultProjectId != null) {
            secrets.put(KEY_PROJECT_ID, connection.defaultProjectId);
            if (connection.defaultProjectName != null) {
                secrets.put(KEY_PROJECT_NAME, connection.defaultProjectName);
            }
        }
        if (connection.sessionCookie != null) {
            secrets.put(KEY_SESSION_COOKIE, connection.sessionCookie);
        }
    }

    /** Setzt oder loescht (null) das Default-Projekt. Ohne Verknuepfung passiert nichts. */
    public void saveDefaultProject(AnkaiProject project) {
        if (load() == null) return;
        if (project == null || isBlank(project.id)) {
            secrets.remove(KEY_PROJECT_ID);
            secrets.remove(KEY_PROJECT_NAME);
            return;
        }
        secrets.put(KEY_PROJECT_ID, project.id);
        if (isBlank(project.name)) {
            secrets.remove(KEY_PROJECT_NAME);
        } else {
            secrets.put(KEY_PROJECT_NAME, project.name);
        }
    }

    /** Uebernimmt das Sessioncookie eines Clients, damit ein Neustart nicht neu anmelden muss. */
    public void saveSessionCookie(String cookie) {
        if (load() == null) return;
        if (isBlank(cookie)) {
            secrets.remove(KEY_SESSION_COOKIE);
        } else {
            secrets.put(KEY_SESSION_COOKIE, cookie);
        }
    }

    /** Entfernt alle gespeicherten Verbindungsdaten. */
    public void clear() {
        secrets.remove(KEY_BASE_URL);
        secrets.remove(KEY_USERNAME);
        secrets.remove(KEY_PASSWORD);
        secrets.remove(KEY_PROJECT_ID);
        secrets.remove(KEY_PROJECT_NAME);
        secrets.remove(KEY_SESSION_COOKIE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
