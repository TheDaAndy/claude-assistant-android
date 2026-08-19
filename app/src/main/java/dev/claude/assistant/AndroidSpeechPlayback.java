package dev.claude.assistant;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.claude.assistant.ankai.SpeechPlayback;

/** Prozesslokaler Android-TTS-Kanal mit sofortigem stop() und sauberem Shutdown. */
final class AndroidSpeechPlayback implements SpeechPlayback, TextToSpeech.OnInitListener {
    private final TextToSpeech tts;
    private final List<String> pending = new ArrayList<>();
    private boolean ready;
    private boolean closed;

    AndroidSpeechPlayback(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public synchronized void onInit(int status) {
        if (closed || status != TextToSpeech.SUCCESS) {
            pending.clear();
            return;
        }
        tts.setLanguage(Locale.GERMAN);
        ready = true;
        for (String text : pending) enqueue(text);
        pending.clear();
    }

    @Override
    public synchronized void speak(String text) {
        if (closed || text == null || text.trim().isEmpty()) return;
        if (!ready) {
            pending.add(text.trim());
            return;
        }
        enqueue(text.trim());
    }

    @Override
    public synchronized void stop() {
        pending.clear();
        tts.stop();
    }

    synchronized void shutdown() {
        if (closed) return;
        closed = true;
        pending.clear();
        tts.stop();
        tts.shutdown();
    }

    private void enqueue(String text) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null,
                "ankai-" + System.nanoTime());
    }
}
