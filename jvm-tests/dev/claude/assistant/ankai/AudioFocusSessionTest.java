package dev.claude.assistant.ankai;

public final class AudioFocusSessionTest {
    public void testFocusCoversWholeSegmentQueue() {
        RecordingGateway gateway = new RecordingGateway();
        RecordingStopper stopper = new RecordingStopper();
        AudioFocusSession session = new AudioFocusSession(gateway, stopper);

        Assert.isTrue("erstes Segment darf starten", session.beginSegment());
        Assert.isTrue("zweites Segment darf starten", session.beginSegment());
        session.finishSegment();

        Assert.eq(1, gateway.requestCount);
        Assert.eq(0, gateway.abandonCount);

        session.finishSegment();
        Assert.eq(1, gateway.abandonCount);
    }

    public void testDeniedFocusSuppressesSegment() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.granted = false;
        AudioFocusSession session = new AudioFocusSession(gateway, new RecordingStopper());

        Assert.isTrue("Segment ohne Fokus muss entfallen", !session.beginSegment());
        session.finishSegment();

        Assert.eq(1, gateway.requestCount);
        Assert.eq(0, gateway.abandonCount);
    }

    public void testFocusLossStopsQueueAndAbandonsFocus() {
        RecordingGateway gateway = new RecordingGateway();
        RecordingStopper stopper = new RecordingStopper();
        AudioFocusSession session = new AudioFocusSession(gateway, stopper);
        session.beginSegment();
        session.beginSegment();

        session.onFocusLost();
        session.finishSegment();

        Assert.eq(1, stopper.stopCount);
        Assert.eq(1, gateway.abandonCount);
    }

    public void testExplicitStopIsIdempotentForFocus() {
        RecordingGateway gateway = new RecordingGateway();
        RecordingStopper stopper = new RecordingStopper();
        AudioFocusSession session = new AudioFocusSession(gateway, stopper);
        session.beginSegment();

        session.stop();
        session.stop();

        Assert.eq(2, stopper.stopCount);
        Assert.eq(1, gateway.abandonCount);
    }

    private static final class RecordingGateway implements AudioFocusSession.Gateway {
        boolean granted = true;
        int requestCount;
        int abandonCount;

        @Override public boolean request() { requestCount++; return granted; }
        @Override public void abandon() { abandonCount++; }
    }

    private static final class RecordingStopper implements AudioFocusSession.PlaybackStopper {
        int stopCount;
        @Override public void stopPlayback() { stopCount++; }
    }
}
