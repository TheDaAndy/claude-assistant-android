package dev.claude.assistant.ankai;

/** Persistente, Android-freie Einstellungen fuer die Sprachausgabe. */
public final class PlaybackSettings {
    private static final String KEY_AUTOPLAY = "playback.autoplay";
    private static final String KEY_ENGINE_PACKAGE = "playback.engine_package";
    private static final String KEY_VOICE_NAME = "playback.voice_name";
    private static final String KEY_SPEECH_RATE = "playback.speech_rate";
    private static final float DEFAULT_SPEECH_RATE = 1.0f;

    private final SecretStore store;

    public PlaybackSettings(SecretStore store) {
        if (store == null) throw new IllegalArgumentException("store fehlt");
        this.store = store;
    }

    /** Autoplay bleibt fuer bestehende Installationen standardmaessig aktiv. */
    public boolean isAutoplayEnabled() {
        return !"false".equals(store.get(KEY_AUTOPLAY));
    }

    public void setAutoplayEnabled(boolean enabled) {
        store.put(KEY_AUTOPLAY, Boolean.toString(enabled));
    }

    /** Null bedeutet: die vom Android-System gewählte Standard-Engine verwenden. */
    public String getEnginePackage() {
        String value = store.get(KEY_ENGINE_PACKAGE);
        if (value == null || !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) return null;
        return value;
    }

    public void setEnginePackage(String packageName) {
        String previous = getEnginePackage();
        String value = packageName == null ? null : packageName.trim();
        if (value == null || !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            store.remove(KEY_ENGINE_PACKAGE);
            value = null;
        } else {
            store.put(KEY_ENGINE_PACKAGE, value);
        }
        if (previous == null ? value != null : !previous.equals(value)) setVoiceName(null);
    }

    /** Null bedeutet: die Standardstimme der ausgewaehlten Engine verwenden. */
    public String getVoiceName() {
        String value = store.get(KEY_VOICE_NAME);
        if (value == null || value.trim().isEmpty() || value.length() > 200
                || !value.matches("[^\\p{Cntrl}]+")) return null;
        return value;
    }

    public void setVoiceName(String voiceName) {
        String value = voiceName == null ? null : voiceName.trim();
        if (value == null || value.isEmpty() || value.length() > 200
                || !value.matches("[^\\p{Cntrl}]+")) {
            store.remove(KEY_VOICE_NAME);
        } else {
            store.put(KEY_VOICE_NAME, value);
        }
    }

    /** Die Auswahl bleibt bewusst in einem gut verstaendlichen Bereich. */
    public float getSpeechRate() {
        String value = store.get(KEY_SPEECH_RATE);
        if (value == null) return DEFAULT_SPEECH_RATE;
        try {
            float rate = Float.parseFloat(value);
            return rate >= 0.8f && rate <= 1.2f ? rate : DEFAULT_SPEECH_RATE;
        } catch (NumberFormatException ignored) {
            return DEFAULT_SPEECH_RATE;
        }
    }

    public void setSpeechRate(float rate) {
        if (!Float.isFinite(rate) || rate < 0.8f || rate > 1.2f) {
            store.remove(KEY_SPEECH_RATE);
        } else {
            store.put(KEY_SPEECH_RATE, Float.toString(rate));
        }
    }
}
