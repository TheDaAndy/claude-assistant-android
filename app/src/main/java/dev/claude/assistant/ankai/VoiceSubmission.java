package dev.claude.assistant.ankai;

import java.io.IOException;

/**
 * Gemeinsamer, Android-freier Einstieg fuer Sprachaufnahmen aus Assistant und Overlay.
 *
 * Laedt die sicher gespeicherte Verknuepfung, setzt das App-Default-Projekt und
 * persistiert ein vom Server erneuertes Sessioncookie. Routing-Fehler werden
 * absichtlich nicht abgefangen, damit die UI deren Kandidaten anzeigen kann.
 */
public final class VoiceSubmission {
    private final AnkaiConnectionStore store;

    public VoiceSubmission(AnkaiConnectionStore store) {
        this.store = store;
    }

    public VoiceResult submit(String filename, String contentType, byte[] audio,
            VoiceProgressListener listener) throws IOException {
        return submit(filename, contentType, audio, null, listener);
    }

    public VoiceResult submitToProject(String filename, String contentType, byte[] audio,
            String projectId, VoiceProgressListener listener) throws IOException {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("Projekt-ID fehlt");
        }
        return submit(filename, contentType, audio, projectId.trim(), listener);
    }

    private VoiceResult submit(String filename, String contentType, byte[] audio,
            String projectId, VoiceProgressListener listener) throws IOException {
        AnkaiConnection connection = store.load();
        if (connection == null) {
            throw new AnkaiAuthException("Bitte zuerst eine Ankai-Instanz verknuepfen");
        }

        AnkaiClient client = connection.newClient();
        VoiceRequest request = new VoiceRequest(filename, contentType, audio);
        if (projectId == null) request.setDefaultProjectId(connection.defaultProjectId);
        else request.setProjectId(projectId);
        try {
            return client.sendVoice(request, listener);
        } finally {
            store.saveSessionCookie(client.sessionCookie());
        }
    }
}
