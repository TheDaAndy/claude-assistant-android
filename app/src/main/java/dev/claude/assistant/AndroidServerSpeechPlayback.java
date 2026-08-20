package dev.claude.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.claude.assistant.ankai.AnkaiClient;
import dev.claude.assistant.ankai.AnkaiConnection;
import dev.claude.assistant.ankai.SpeechPlayback;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

/** Spielt authentifiziert vom Ankaï-Piper-Endpunkt erzeugte WAV-Segmente ab. */
final class AndroidServerSpeechPlayback implements SpeechPlayback {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile MediaPlayer player;
    private volatile boolean closed;

    AndroidServerSpeechPlayback(Context context) {
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop();
                }).build();
    }

    @Override public void speak(String text) {
        if (closed || text == null || text.trim().isEmpty()) return;
        final int expectedGeneration = generation.get();
        executor.execute(() -> downloadAndPlay(text.trim(), expectedGeneration));
    }

    private void downloadAndPlay(String text, int expectedGeneration) {
        File audio = null;
        try {
            AnkaiConnection connection = EncryptedPrefsSecretStore.connectionStore(context).load();
            if (connection == null || closed || generation.get() != expectedGeneration) return;
            AnkaiClient client = connection.newClient();
            byte[] wav = client.synthesizeSpeech(text);
            if (closed || generation.get() != expectedGeneration) return;
            audio = File.createTempFile("ankai-tts-", ".wav", context.getCacheDir());
            try (FileOutputStream out = new FileOutputStream(audio)) { out.write(wav); }
            if (audioManager == null || audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
            MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            next.setDataSource(audio.getAbsolutePath());
            File completedAudio = audio;
            next.setOnCompletionListener(mp -> finish(mp, completedAudio));
            next.setOnErrorListener((mp, what, extra) -> { finish(mp, completedAudio); return true; });
            next.prepare();
            if (closed || generation.get() != expectedGeneration) { finish(next, audio); return; }
            player = next;
            next.start();
            audio = null;
            while (!closed && generation.get() == expectedGeneration && next.isPlaying()) Thread.sleep(40);
        } catch (Exception ignored) {
            // Sichtbarer Text bleibt erhalten; ein TTS-Fehler darf den Chatlauf nicht abbrechen.
        } finally {
            if (audio != null) audio.delete();
        }
    }

    private synchronized void finish(MediaPlayer target, File audio) {
        if (player == target) player = null;
        try { target.release(); } catch (RuntimeException ignored) { }
        audio.delete();
        if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    @Override public synchronized void stop() {
        generation.incrementAndGet();
        MediaPlayer current = player;
        player = null;
        if (current != null) {
            try { current.stop(); } catch (RuntimeException ignored) { }
            try { current.release(); } catch (RuntimeException ignored) { }
        }
        if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    synchronized void shutdown() {
        if (closed) return;
        closed = true;
        stop();
        executor.shutdownNow();
    }
}

