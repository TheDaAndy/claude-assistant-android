package dev.claude.assistant.ankai;

/** Persistente, Android-freie Einstellungen fuer die Sprachausgabe. */
public final class PlaybackSettings {
    private static final String KEY_AUTOPLAY = "playback.autoplay";
    private static final String KEY_ENGINE_PACKAGE = "playback.engine_package";

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
        String value = packageName == null ? null : packageName.trim();
        if (value == null || !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            store.remove(KEY_ENGINE_PACKAGE);
        } else {
            store.put(KEY_ENGINE_PACKAGE, value);
        }
    }
}
