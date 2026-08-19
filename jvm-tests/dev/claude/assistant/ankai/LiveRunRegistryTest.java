package dev.claude.assistant.ankai;

public final class LiveRunRegistryTest {

    public void testEventsContinueAfterOverlayClosesButSpeechStaysBlocked() {
        LiveRunRegistry registry = new LiveRunRegistry();
        LiveRunState run = registry.start("session-1", "run-1");
        run.attachOverlay();
        run.accept(new LiveRunEvent("assistant", "Erster Teil"));

        run.closeOverlay();
        run.accept(new LiveRunEvent("assistant", "Zweiter Teil"));
        run.accept(new LiveRunEvent("done", null));

        Assert.eq("Erster Teil\n\nZweiter Teil", run.text());
        Assert.isTrue("Lauf muss beendet sein", run.isDone());
        Assert.isTrue("Overlay muss abgemeldet sein", !run.isOverlayAttached());
        Assert.isTrue("Schliessen sperrt jede spaetere Sprachausgabe", !run.maySpeak());
    }

    public void testReopeningOverlayDoesNotReenableSpeech() {
        LiveRunState run = new LiveRunRegistry().start("session-1", "run-1");
        run.attachOverlay();
        run.closeOverlay();
        run.attachOverlay();

        Assert.isTrue("Overlay darf erneut angezeigt werden", run.isOverlayAttached());
        Assert.isTrue("Sprachsperre bleibt fuer diesen Lauf bestehen", !run.maySpeak());
    }

    public void testParallelRunsKeepIndependentState() {
        LiveRunRegistry registry = new LiveRunRegistry();
        LiveRunState first = registry.start("session-1", "run-1");
        LiveRunState second = registry.start("session-2", "run-2");
        first.closeOverlay();
        second.accept(new LiveRunEvent("assistant", "Nur Lauf zwei"));

        Assert.eq(2, registry.size());
        Assert.eq("", first.text());
        Assert.eq("Nur Lauf zwei", second.text());
        Assert.isTrue("anderer Lauf darf weiter sprechen", second.maySpeak());
    }

    public void testStartIsIdempotentForReconnect() {
        LiveRunRegistry registry = new LiveRunRegistry();
        LiveRunState original = registry.start("session-1", "run-1");
        original.accept(new LiveRunEvent("assistant", "Schon da"));

        LiveRunState reconnected = registry.start("session-1", "run-neu");

        Assert.isTrue("Reconnect muss vorhandenen Zustand behalten", original == reconnected);
        Assert.eq("Schon da", reconnected.text());
        Assert.eq("run-1", reconnected.runId());
    }

    public void testSessionIdIsRequired() {
        try {
            new LiveRunRegistry().start(" ", "run-1");
            Assert.fail("Leere Session-ID muss abgewiesen werden");
        } catch (IllegalArgumentException expected) {
        }
    }
}
