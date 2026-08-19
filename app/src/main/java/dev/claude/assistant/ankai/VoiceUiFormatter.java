package dev.claude.assistant.ankai;

import java.util.StringJoiner;

/** Android-freie Texte fuer Fortschritt, Ergebnis und Routingfehler. */
public final class VoiceUiFormatter {
    private VoiceUiFormatter() {}

    public static String progress(int percent, String stage) {
        String label = stage == null || stage.trim().isEmpty() ? "Verarbeitung" : stage.trim();
        return "Ankaï verarbeitet die Aufnahme: " + label + " (" + percent + " %)";
    }

    public static String result(VoiceResult result) {
        String transcript = result.transcript == null || result.transcript.trim().isEmpty()
                ? "Aufnahme an Ankaï übergeben" : result.transcript.trim();
        return transcript + "\n\nChat: " + safe(result.sessionId) + "\nLauf: " + safe(result.runId);
    }

    public static String error(Throwable error) {
        String message = error.getMessage() == null ? "Ankaï-Anfrage fehlgeschlagen" : error.getMessage();
        if (!(error instanceof AnkaiRoutingException)) return message;
        AnkaiRoutingException routing = (AnkaiRoutingException) error;
        if (routing.candidates.isEmpty()) return message;
        StringJoiner names = new StringJoiner(", ");
        for (AnkaiProject project : routing.candidates) names.add(project.toString());
        return message + "\nMögliche Projekte: " + names;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "–" : value.trim();
    }
}
