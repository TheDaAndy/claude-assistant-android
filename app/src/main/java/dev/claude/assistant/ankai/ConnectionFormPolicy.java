package dev.claude.assistant.ankai;

/** Sicherheits- und Bedienregel fuer sensible Eingaben im Verknuepfungsformular. */
public final class ConnectionFormPolicy {
    private ConnectionFormPolicy() {}

    /**
     * Nach erfolgreicher Anmeldung verschwindet das Passwort aus dem Formular.
     * Bei einem Fehler bleibt es fuer eine direkte Korrektur oder Wiederholung erhalten.
     */
    public static boolean shouldClearPassword(ConnectionUiState state) {
        return state != null && state.connected;
    }
}
