package dev.claude.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.claude.assistant.ankai.SpeechPlayback;
import dev.claude.assistant.ankai.AudioFocusSession;
import dev.claude.assistant.ankai.PlaybackSettings;

/** Prozesslokaler Android-TTS-Kanal mit sofortigem stop() und sauberem Shutdown. */
final class AndroidSpeechPlayback implements SpeechPlayback, TextToSpeech.OnInitListener {
    private final TextToSpeech tts;
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final AudioFocusSession focusSession;
    private final List<String> pending = new ArrayList<>();
    private boolean ready;
    private boolean closed;
    private final String voiceName;

    AndroidSpeechPlayback(Context context, PlaybackSettings settings) {
        Context applicationContext = context.getApplicationContext();
        String enginePackage = settings == null ? null : settings.getEnginePackage();
        voiceName = settings == null ? null : settings.getVoiceName();
        tts = enginePackage == null
                ? new TextToSpeech(applicationContext, this)
                : new TextToSpeech(applicationContext, this, enginePackage);
        audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                .build();
        focusSession = new AudioFocusSession(new AudioFocusSession.Gateway() {
            @Override public boolean request() {
                return audioManager != null && audioManager.requestAudioFocus(focusRequest)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }

            @Override public void abandon() {
                if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
            }
        }, tts::stop);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { focusSession.finishSegment(); }
            @Override public void onError(String utteranceId) { focusSession.finishSegment(); }
        });
    }

    @Override
    public synchronized void onInit(int status) {
        if (closed || status != TextToSpeech.SUCCESS) {
            pending.clear();
            return;
        }
        tts.setLanguage(Locale.GERMAN);
        if (voiceName != null && tts.getVoices() != null) {
            for (Voice voice : tts.getVoices()) {
                if (voiceName.equals(voice.getName())) {
                    tts.setVoice(voice);
                    break;
                }
            }
        }
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
        focusSession.stop();
    }

    synchronized void shutdown() {
        if (closed) return;
        closed = true;
        pending.clear();
        focusSession.stop();
        tts.shutdown();
    }

    private void enqueue(String text) {
        if (!focusSession.beginSegment()) return;
        int result = tts.speak(text, TextToSpeech.QUEUE_ADD, null,
                "ankai-" + System.nanoTime());
        if (result == TextToSpeech.ERROR) focusSession.finishSegment();
    }

    private void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            focusSession.onFocusLost();
        }
    }
}
