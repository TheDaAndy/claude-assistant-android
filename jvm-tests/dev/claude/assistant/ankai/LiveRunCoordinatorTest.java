package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class LiveRunCoordinatorTest {

    public void testTracksVoiceResultAndForgetsRunAfterDone() throws Exception {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);
        LiveRunCoordinator coordinator = new LiveRunCoordinator(store);

        LiveRunState state = coordinator.track(new VoiceResult("session-1", "run-1", "Hallo"));
        boolean active = coordinator.reconnect("session-1", (sessionId, listener) -> {
            listener.onEvent(new LiveRunEvent("assistant", "Antwort"));
            listener.onEvent(new LiveRunEvent("done", null));
            return true;
        });

        Assert.isTrue("Stream war aktiv", active);
        Assert.eq("Antwort", state.text());
        Assert.isTrue("Lauf ist beendet", state.isDone());
        Assert.eq(0, store.load().size());
    }

    public void testInactiveRunIsForgottenAfterProcessRestart() throws Exception {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);
        store.remember("session-1", "run-1");
        LiveRunCoordinator restarted = new LiveRunCoordinator(store);

        boolean active = restarted.reconnect("session-1", (sessionId, listener) -> false);

        Assert.isTrue("Server meldet inaktiv", !active);
        Assert.eq(0, store.load().size());
        Assert.eq("run-1", restarted.registry().get("session-1").runId());
    }

    public void testNetworkFailureKeepsRunForLaterReconnect() {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);
        LiveRunCoordinator coordinator = new LiveRunCoordinator(store);
        coordinator.track(new VoiceResult("session-1", "run-1", null));

        try {
            coordinator.reconnect("session-1", (sessionId, listener) -> {
                throw new IOException("offline");
            });
            Assert.fail("Netzwerkfehler erwartet");
        } catch (IOException expected) {
        }

        Assert.eq(1, store.load().size());
        Assert.eq("session-1", store.load().get(0).sessionId());
    }

    private static final class MemorySecretStore implements SecretStore {
        private final Map<String, String> values = new HashMap<>();
        public String get(String key) { return values.get(key); }
        public void put(String key, String value) { values.put(key, value); }
        public void remove(String key) { values.remove(key); }
    }
}
