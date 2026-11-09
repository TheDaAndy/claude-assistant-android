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
        int end = snapshot.isDone() ? current.length() : SpeechText.stableEnd(current);
        String stable = current.substring(0, end);
        String addition = stable.startsWith(spokenText)
                ? stable.substring(spokenText.length()).trim()
                : stable.trim();
        spokenText = stable;
        addition = SpeechText.forPlayback(addition);
        if (!addition.isEmpty()) run.speakIfAllowed(playback, addition);
    }

    @Override
    public void close() {
        subscription.close();
        run.clearSpeechPlayback(playback);
    }
}
