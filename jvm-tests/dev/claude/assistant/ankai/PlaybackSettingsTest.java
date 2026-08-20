package dev.claude.assistant.ankai;

/** Tests fuer die persistente Autoplay-Einstellung. */
public final class PlaybackSettingsTest {

    public void testAutoplayIstStandardmaessigAktiv() {
        PlaybackSettings settings = new PlaybackSettings(
                new AnkaiConnectionStoreTest.MemoryStore());

        Assert.isTrue("bestehendes Autoplay bleibt Standard", settings.isAutoplayEnabled());
    }

    public void testAutoplayKannDauerhaftDeaktiviertUndAktiviertWerden() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings first = new PlaybackSettings(store);

        first.setAutoplayEnabled(false);
        Assert.isTrue("deaktiviert", !new PlaybackSettings(store).isAutoplayEnabled());

        first.setAutoplayEnabled(true);
        Assert.isTrue("wieder aktiviert", new PlaybackSettings(store).isAutoplayEnabled());
    }

    public void testNurExplizitesFalseDeaktiviertAutoplay() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        store.put("playback.autoplay", "beschaedigt");

        Assert.isTrue("ungueltiger Wert faellt sicher auf Standard zurueck",
                new PlaybackSettings(store).isAutoplayEnabled());
    }

}
