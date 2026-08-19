package dev.claude.assistant.ankai;

/** Hält höchstens eine kurzlebige TTS-Stimmenvorschau aktiv. */
public final class VoicePreviewController {
    public interface Channel {
        void speak(String text);
        void close();
    }

    public interface Factory {
        Channel create();
    }

    private final Factory factory;
    private Channel current;

    public VoicePreviewController(Factory factory) {
        if (factory == null) throw new IllegalArgumentException("Factory fehlt");
        this.factory = factory;
    }

    public synchronized void preview(String text) {
        if (text == null || text.trim().isEmpty()) return;
        close();
        current = factory.create();
        current.speak(text.trim());
    }

    public synchronized void close() {
        if (current == null) return;
        current.close();
        current = null;
    }
}
