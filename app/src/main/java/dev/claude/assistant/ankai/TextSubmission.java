package dev.claude.assistant.ankai;

import java.io.IOException;

/** Sicherer, Android-freier Einstieg fuer Texteingaben aus den Assistant-Oberflaechen. */
public final class TextSubmission {
    private final AnkaiConnectionStore store;
    private final LiveRunCoordinator liveRuns;

    public TextSubmission(AnkaiConnectionStore store, LiveRunCoordinator liveRuns) {
        if (store == null || liveRuns == null) throw new IllegalArgumentException("Ablage oder Laufzustand fehlt");
        this.store = store;
        this.liveRuns = liveRuns;
    }

    public String submit(String message) throws IOException {
        AnkaiConnection connection = store.load();
        if (connection == null) throw new AnkaiAuthException("Bitte zuerst eine Ankai-Instanz verknuepfen");
        AnkaiClient client = connection.newClient();
        final LiveRunState[] state = new LiveRunState[1];
        try {
            return client.sendText(new TextRequest(message, connection.defaultProjectId), event -> {
                if ("session".equals(event.type)) {
                    state[0] = liveRuns.track(event.text, null);
                } else if (state[0] != null) {
                    liveRuns.accept(state[0].sessionId(), event);
                }
            });
        } finally {
            store.saveSessionCookie(client.sessionCookie());
        }
    }
}
