package dev.claude.assistant.ankai;

import java.io.IOException;

/**
 * Haelt den Stream eines persistierten Laufs verbunden, bis der Server dessen
 * Ende bestaetigt oder der aufrufende Dienst die Wartephase abbricht.
 */
public final class LiveRunReconnectLoop {
    private static final long INITIAL_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 30_000L;
    private static final int MAX_STARTING_INACTIVE_RETRIES = 3;

    private final LiveRunCoordinator coordinator;
    private final RetryWaiter waiter;

    public LiveRunReconnectLoop(LiveRunCoordinator coordinator, RetryWaiter waiter) {
        if (coordinator == null) throw new IllegalArgumentException("Laufkoordination fehlt");
        if (waiter == null) throw new IllegalArgumentException("Retry-Wartefunktion fehlt");
        this.coordinator = coordinator;
        this.waiter = waiter;
    }

    public void run(String sessionId, LiveRunCoordinator.LiveStream stream) {
        run(sessionId, null, stream);
    }

    public void run(String sessionId, LiveRunCoordinator.HistoryLoader history,
                    LiveRunCoordinator.LiveStream stream) {
        int retry = 0;
        int startingInactiveRetries = 0;
        while (shouldReconnect(sessionId)) {
            try {
                boolean active = coordinator.reconnect(sessionId, history, stream);
                if (!active) {
                    if (!shouldReconnect(sessionId)) return;
                    if (++startingInactiveRetries >= MAX_STARTING_INACTIVE_RETRIES) return;
                } else {
                    startingInactiveRetries = 0;
                }
            } catch (AnkaiAuthException expiredConnection) {
                return;
            } catch (IOException transportFailure) {
                // Der persistierte Lauf bleibt bestehen und wird unten erneut versucht.
            }
            if (!waiter.await(delayFor(retry++))) return;
        }
    }

    private boolean shouldReconnect(String sessionId) {
        LiveRunState state = coordinator.registry().get(sessionId);
        return state != null && !state.isDone();
    }

    static long delayFor(int retry) {
        long delay = INITIAL_DELAY_MS << Math.min(Math.max(retry, 0), 5);
        return Math.min(delay, MAX_DELAY_MS);
    }

    @FunctionalInterface
    public interface RetryWaiter {
        /** @return false, wenn der aufrufende Dienst beendet wurde. */
        boolean await(long delayMillis);
    }
}
