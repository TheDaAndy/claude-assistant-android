package dev.claude.assistant.ankai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-sicherer lokaler Zustand eines sichtbaren Chatlaufs.
 *
 * Der Zustand gehoert zum Lauf und nicht zum Overlay. Dadurch darf die UI
 * verschwinden, waehrend Netzwerkereignisse weiter verarbeitet werden. Eine
 * beim Schliessen gesetzte Sprachsperre kann fuer denselben Lauf nicht
 * versehentlich durch erneutes Anzeigen aufgehoben werden.
 */
public final class LiveRunState implements LiveRunListener {

    private final String sessionId;
    private final String runId;
    private final StringBuilder text = new StringBuilder();
    private final List<LiveRunObserver> observers = new CopyOnWriteArrayList<>();
    private boolean overlayAttached;
    private boolean speechAllowed = true;
    private boolean done;

    LiveRunState(String sessionId, String runId) {
        this.sessionId = sessionId;
        this.runId = blankToNull(runId);
    }

    public String sessionId() {
        return sessionId;
    }

    public String runId() {
        return runId;
    }

    public synchronized String text() {
        return text.toString();
    }

    public synchronized boolean isOverlayAttached() {
        return overlayAttached;
    }

    public synchronized boolean maySpeak() {
        return speechAllowed;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized void attachOverlay() {
        overlayAttached = true;
    }

    /** Meldet die UI ab und sperrt Autoplay fuer die gesamte Restlaufzeit. */
    public synchronized void closeOverlay() {
        overlayAttached = false;
        speechAllowed = false;
    }

    /**
     * Meldet eine UI an und liefert sofort den aktuellen Zustand. Netzwerk-
     * Callbacks laufen auf Hintergrundthreads; die Android-UI muss daher bei
     * Bedarf selbst auf ihren Main-Thread wechseln.
     */
    public LiveRunSubscription observe(LiveRunObserver observer) {
        if (observer == null) throw new IllegalArgumentException("Observer fehlt");
        observers.add(observer);
        observer.onChanged(snapshot());
        return () -> observers.remove(observer);
    }

    public synchronized LiveRunSnapshot snapshot() {
        return new LiveRunSnapshot(sessionId, runId, text.toString(), done, speechAllowed);
    }

    @Override
    public void onEvent(LiveRunEvent event) {
        accept(event);
    }

    public void accept(LiveRunEvent event) {
        if (event == null || event.type == null) return;
        LiveRunSnapshot changed = null;
        synchronized (this) {
            if ("assistant".equals(event.type)) {
                String addition = blankToNull(event.text);
                if (addition != null) {
                    if (text.length() > 0) text.append("\n\n");
                    text.append(addition);
                    changed = snapshot();
                }
            } else if ("done".equals(event.type)) {
                done = true;
                changed = snapshot();
            }
        }
        if (changed != null) {
            for (LiveRunObserver observer : observers) observer.onChanged(changed);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
