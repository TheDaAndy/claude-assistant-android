package dev.claude.assistant.ankai;

/** Persistente, Android-freie Einstellungen fuer die Sprachausgabe. */
public final class PlaybackSettings {
    private static final String KEY_AUTOPLAY = "playback.autoplay";

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
}
