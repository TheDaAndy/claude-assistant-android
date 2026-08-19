package dev.claude.assistant.ankai;

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

    @Override
    public void onEvent(LiveRunEvent event) {
        accept(event);
    }

    public synchronized void accept(LiveRunEvent event) {
        if (event == null || event.type == null) return;
        if ("assistant".equals(event.type)) {
            String addition = blankToNull(event.text);
            if (addition != null) {
                if (text.length() > 0) text.append("\n\n");
                text.append(addition);
            }
        } else if ("done".equals(event.type)) {
            done = true;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
