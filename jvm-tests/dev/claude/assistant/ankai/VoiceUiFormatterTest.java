package dev.claude.assistant.ankai;

import java.util.List;

public final class VoiceUiFormatterTest {

    public void testProgressIncludesPercentAndStage() {
        assertEquals("Ankaï verarbeitet die Aufnahme: Transkription (35 %)",
                VoiceUiFormatter.progress(35, "Transkription"));
    }

    public void testResultIncludesTranscriptAndRunIdentifiers() {
        VoiceResult result = new VoiceResult("session-1", "run-2", "Hallo Ankaï");
        assertEquals("Hallo Ankaï\n\nChat: session-1\nLauf: run-2", VoiceUiFormatter.result(result));
    }

    public void testRoutingErrorListsCandidates() {
        AnkaiRoutingException error = new AnkaiRoutingException(
                "Projekt ist mehrdeutig",
                "project_ambiguous",
                List.of(new AnkaiProject("p1", "Alpha"), new AnkaiProject("p2", "Beta")));
        assertEquals("Projekt ist mehrdeutig\nMögliche Projekte: Alpha, Beta",
                VoiceUiFormatter.error(error));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
