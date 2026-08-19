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

    public void testSystemstandardIstDieVoreingestellteEngine() {
        PlaybackSettings settings = new PlaybackSettings(
                new AnkaiConnectionStoreTest.MemoryStore());

        Assert.isTrue("keine feste Engine", settings.getEnginePackage() == null);
    }

    public void testEngineKannGespeichertUndAufSystemstandardZurueckgesetztWerden() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings settings = new PlaybackSettings(store);

        settings.setEnginePackage("com.example.offline.tts");
        Assert.eq("com.example.offline.tts",
                new PlaybackSettings(store).getEnginePackage());

        settings.setEnginePackage(null);
        Assert.isTrue("Systemstandard nach Reset",
                new PlaybackSettings(store).getEnginePackage() == null);
    }

    public void testLeereOderBeschaedigteEngineFaelltAufSystemstandardZurueck() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings settings = new PlaybackSettings(store);

        settings.setEnginePackage("   ");
        Assert.isTrue("Leerwert", settings.getEnginePackage() == null);

        store.put("playback.engine_package", "bad package name");
        Assert.isTrue("ungueltiger Paketname",
                new PlaybackSettings(store).getEnginePackage() == null);
    }

    public void testStimmeKannGespeichertUndZurueckgesetztWerden() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings settings = new PlaybackSettings(store);

        settings.setVoiceName("de-de-x-deb-local");
        Assert.eq("de-de-x-deb-local", new PlaybackSettings(store).getVoiceName());

        settings.setVoiceName(null);
        Assert.isTrue("Engine-Standard nach Reset",
                new PlaybackSettings(store).getVoiceName() == null);
    }

    public void testEnginewechselSetztStimmeZurueck() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings settings = new PlaybackSettings(store);
        settings.setEnginePackage("com.example.first.tts");
        settings.setVoiceName("first-voice");

        settings.setEnginePackage("com.example.second.tts");

        Assert.isTrue("Stimme gehoert nicht zur neuen Engine", settings.getVoiceName() == null);
    }

    public void testUngueltigerStimmennameWirdNichtVerwendet() {
        AnkaiConnectionStoreTest.MemoryStore store = new AnkaiConnectionStoreTest.MemoryStore();
        PlaybackSettings settings = new PlaybackSettings(store);

        settings.setVoiceName("   ");
        Assert.isTrue("Leerwert", settings.getVoiceName() == null);

        store.put("playback.voice_name", "voice\nname");
        Assert.isTrue("Steuerzeichen", new PlaybackSettings(store).getVoiceName() == null);
    }
}
