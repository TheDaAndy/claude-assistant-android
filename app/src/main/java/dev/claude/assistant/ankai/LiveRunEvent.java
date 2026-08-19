package dev.claude.assistant.ankai;

/** Ein normalisiertes Ereignis aus dem Live-Stream eines sichtbaren Chatlaufs. */
public final class LiveRunEvent {
    public final String type;
    public final String text;

    public LiveRunEvent(String type, String text) {
        this.type = type;
        this.text = text;
    }
}
