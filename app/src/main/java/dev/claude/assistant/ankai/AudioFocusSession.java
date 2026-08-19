package dev.claude.assistant.ankai;

/** Verwaltet Audiofokus ueber eine Folge gequeueter TTS-Segmente. */
public final class AudioFocusSession {
    public interface Gateway {
        boolean request();
        void abandon();
    }

    public interface PlaybackStopper {
        void stopPlayback();
    }

    private final Gateway gateway;
    private final PlaybackStopper stopper;
    private int queuedSegments;
    private boolean focused;

    public AudioFocusSession(Gateway gateway, PlaybackStopper stopper) {
        if (gateway == null || stopper == null) throw new IllegalArgumentException();
        this.gateway = gateway;
        this.stopper = stopper;
    }

    /** Reserviert Fokus fuer ein weiteres Segment. Bei Ablehnung wird es nicht gesprochen. */
    public synchronized boolean beginSegment() {
        if (!focused) {
            focused = gateway.request();
            if (!focused) return false;
        }
        queuedSegments++;
        return true;
    }

    /** Meldet Abschluss oder Fehler eines zuvor reservierten Segments. */
    public synchronized void finishSegment() {
        if (queuedSegments > 0) queuedSegments--;
        if (queuedSegments == 0) abandonFocus();
    }

    /** Beendet die komplette Queue, wenn Android den Fokus entzieht. */
    public synchronized void onFocusLost() {
        if (!focused && queuedSegments == 0) return;
        queuedSegments = 0;
        stopper.stopPlayback();
        abandonFocus();
    }

    /** Expliziter Nutzer-/Lifecycle-Stopp. */
    public synchronized void stop() {
        queuedSegments = 0;
        stopper.stopPlayback();
        abandonFocus();
    }

    private void abandonFocus() {
        if (!focused) return;
        focused = false;
        gateway.abandon();
    }
}
