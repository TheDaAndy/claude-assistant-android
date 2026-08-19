package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LiveRunReconnectLoopTest {
    public void testRetriesActiveStreamAfterEofAndStopsOnDone() throws Exception {
        LiveRunCoordinator coordinator = coordinatorWith("session-1");
        AtomicInteger opens = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        LiveRunReconnectLoop loop = new LiveRunReconnectLoop(coordinator, delay -> {
            delays.add(delay);
            return true;
        });

        loop.run("session-1", (sessionId, listener) -> {
            if (opens.incrementAndGet() == 1) return true;
            listener.onEvent(new LiveRunEvent("done", "fertig"));
            return false;
        });

        Assert.eq(2, opens.get());
        Assert.eq(List.of(1_000L), delays);
    }

    public void testRetriesIOExceptionWithCappedExponentialBackoff() throws Exception {
        LiveRunCoordinator coordinator = coordinatorWith("session-2");
        AtomicInteger opens = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        LiveRunReconnectLoop loop = new LiveRunReconnectLoop(coordinator, delay -> {
            delays.add(delay);
            return true;
        });

        loop.run("session-2", (sessionId, listener) -> {
            int attempt = opens.incrementAndGet();
            if (attempt <= 7) throw new IOException("offline");
            listener.onEvent(new LiveRunEvent("done", "fertig"));
            return false;
        });

        Assert.eq(8, opens.get());
        Assert.eq(
            List.of(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            delays
        );
    }

    public void testInterruptionStopsWithoutForgettingRun() throws Exception {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);
        store.remember("session-3", "run-3");
        LiveRunCoordinator coordinator = new LiveRunCoordinator(store);
        LiveRunReconnectLoop loop = new LiveRunReconnectLoop(coordinator, delay -> false);

        loop.run("session-3", (sessionId, listener) -> {
            throw new IOException("offline");
        });

        Assert.eq(1, store.load().size());
        Assert.eq("session-3", store.load().get(0).sessionId());
    }

    public void testExpiredLoginStopsWithoutRetryStorm() {
        LiveRunCoordinator coordinator = coordinatorWith("session-4");
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();
        LiveRunReconnectLoop loop = new LiveRunReconnectLoop(coordinator, delay -> {
            waits.incrementAndGet();
            return true;
        });

        loop.run("session-4", (sessionId, listener) -> {
            opens.incrementAndGet();
            throw new AnkaiAuthException("abgelaufen");
        });

        Assert.eq(1, opens.get());
        Assert.eq(0, waits.get());
    }

    private static LiveRunCoordinator coordinatorWith(String sessionId) {
        ActiveRunStore store = new ActiveRunStore(new MemorySecretStore());
        store.remember(sessionId, "run");
        return new LiveRunCoordinator(store);
    }

    private static final class MemorySecretStore implements SecretStore {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }
}
