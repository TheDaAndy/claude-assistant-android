package dev.claude.assistant.ankai;

/** Wiedergabekanal fuer laufbezogene Sprachausgabe. */
public interface SpeechPlayback {
    void speak(String text);
    void stop();
}
