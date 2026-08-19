package dev.claude.assistant.ankai;

public final class VoicePreviewControllerTest {
    public void testPreviewReplacesEarlierChannel() {
        FakeFactory factory = new FakeFactory();
        VoicePreviewController controller = new VoicePreviewController(factory);

        controller.preview("Erste Vorschau");
        FakeChannel first = factory.latest;
        controller.preview("Zweite Vorschau");

        Assert.isTrue("erster Kanal geschlossen", first.closed);
        Assert.eq("Zweite Vorschau", factory.latest.spoken);
    }

    public void testCloseStopsCurrentPreviewAndBlankTextIsIgnored() {
        FakeFactory factory = new FakeFactory();
        VoicePreviewController controller = new VoicePreviewController(factory);

        controller.preview("   ");
        Assert.eq(0, factory.created);
        controller.preview("Beispiel");
        FakeChannel current = factory.latest;
        controller.close();

        Assert.isTrue("aktueller Kanal geschlossen", current.closed);
    }

    private static final class FakeFactory implements VoicePreviewController.Factory {
        int created;
        FakeChannel latest;

        @Override public VoicePreviewController.Channel create() {
            created++;
            latest = new FakeChannel();
            return latest;
        }
    }

    private static final class FakeChannel implements VoicePreviewController.Channel {
        String spoken;
        boolean closed;

        @Override public void speak(String text) { spoken = text; }
        @Override public void close() { closed = true; }
    }
}
