package dev.claude.assistant.ankai;

/**
 * Spricht nur neu hinzugekommene Antwortteile und bindet den sofortigen
 * Wiedergabestopp an die irreversible Sprachsperre des Laufs.
 */
public final class LiveRunSpeechController implements LiveRunObserver, AutoCloseable {
    private final LiveRunState run;
    private final SpeechPlayback playback;
    private final LiveRunSubscription subscription;
    private String spokenText = "";

    public LiveRunSpeechController(LiveRunState run, SpeechPlayback playback) {
        if (run == null) throw new IllegalArgumentException("Lauf fehlt");
        if (playback == null) throw new IllegalArgumentException("Wiedergabe fehlt");
        this.run = run;
        this.playback = playback;
        run.setSpeechPlayback(playback);
        this.subscription = run.observe(this);
    }

    @Override
    public synchronized void onChanged(LiveRunSnapshot snapshot) {
        String current = snapshot.text() == null ? "" : snapshot.text();
        if (!snapshot.maySpeak()) {
            spokenText = current;
            return;
        }
        String addition = current.startsWith(spokenText)
                ? current.substring(spokenText.length()).trim()
                : current.trim();
        spokenText = current;
        if (!addition.isEmpty()) run.speakIfAllowed(playback, addition);
    }

    @Override
    public void close() {
        subscription.close();
        run.clearSpeechPlayback(playback);
    }
}
