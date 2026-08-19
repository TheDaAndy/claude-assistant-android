package dev.claude.assistant.ankai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tests fuer die persistierte Ankai-Verknuepfung. */
public final class AnkaiConnectionStoreTest {

    /** In-Memory-Ersatz fuer die EncryptedSharedPreferences. */
    static final class MemoryStore implements SecretStore {
        final Map<String, String> values = new LinkedHashMap<>();

        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }

    public void testSaveAndLoadRoundTrip() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);

        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));

        AnkaiConnection loaded = store.load();
        Assert.isTrue("Verknuepfung geladen", (loaded) != null);
        Assert.eq("https://chat.example.org", loaded.baseUrl);
        Assert.eq("anka", loaded.username);
        Assert.eq("geheim", loaded.password);
        Assert.isTrue("kein Default-Projekt", (loaded.defaultProjectId) == null);
        Assert.isTrue("verbunden", store.isConnected());
    }

    public void testUrlWirdBeimSpeichernNormalisiert() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);

        store.save(new AnkaiConnection("chat.example.org/", "anka", "geheim"));

        Assert.eq("https://chat.example.org", store.load().baseUrl);
    }

    public void testLeereUrlWirdAbgelehnt() {
        boolean thrown = false;
        try {
            new AnkaiConnection("   ", "anka", "geheim");
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        Assert.isTrue("leere URL wirft", thrown);
    }

    public void testOhneVerknuepfungIstLoadNull() {
        AnkaiConnectionStore store = new AnkaiConnectionStore(new MemoryStore());
        Assert.isTrue("nichts gespeichert", (store.load()) == null);
        Assert.isTrue("nicht verbunden", !(store.isConnected()));
    }

    public void testUnvollstaendigeAblageGiltAlsNichtVerknuepft() {
        MemoryStore secrets = new MemoryStore();
        secrets.put("ankai.baseUrl", "https://chat.example.org");
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        Assert.isTrue("ohne Zugangsdaten keine Verknuepfung", (store.load()) == null);
    }

    public void testDefaultProjektAktualisieren() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));

        store.saveDefaultProject(new AnkaiProject("proj-1", "Ankai assistant"));

        AnkaiConnection loaded = store.load();
        Assert.eq("proj-1", loaded.defaultProjectId);
        Assert.eq("Ankai assistant", loaded.defaultProjectName);
        Assert.eq("geheim", loaded.password);
    }

    public void testDefaultProjektEntfernen() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));
        store.saveDefaultProject(new AnkaiProject("proj-1", "Ankai assistant"));

        store.saveDefaultProject(null);

        Assert.isTrue("Default entfernt", (store.load().defaultProjectId) == null);
    }

    public void testDefaultProjektOhneVerknuepfungWirdIgnoriert() {
        AnkaiConnectionStore store = new AnkaiConnectionStore(new MemoryStore());
        store.saveDefaultProject(new AnkaiProject("proj-1", "Ankai assistant"));
        Assert.isTrue("weiterhin nicht verknuepft", (store.load()) == null);
    }

    public void testClearLoeschtAlleGeheimnisse() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));
        store.saveSessionCookie("connect.sid=abc");

        store.clear();

        Assert.isTrue("keine Verknuepfung", (store.load()) == null);
        Assert.isTrue("Ablage leer: " + secrets.values.keySet(), secrets.values.isEmpty());
    }

    public void testSessionCookieWirdGespeichertUndGeladen() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));

        store.saveSessionCookie("connect.sid=abc");

        Assert.eq("connect.sid=abc", store.load().sessionCookie);
    }

    public void testNeueVerknuepfungVerwirftAltesCookieUndDefault() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));
        store.saveSessionCookie("connect.sid=abc");
        store.saveDefaultProject(new AnkaiProject("proj-1", "Alt"));

        store.save(new AnkaiConnection("andere.example.org", "bob", "anders"));

        AnkaiConnection loaded = store.load();
        Assert.isTrue("Cookie verworfen", (loaded.sessionCookie) == null);
        Assert.isTrue("Default verworfen", (loaded.defaultProjectId) == null);
        Assert.eq("bob", loaded.username);
    }

    public void testClientUebernimmtCookieUndEndpoint() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));
        store.saveSessionCookie("connect.sid=abc");

        AnkaiClient client = store.load().newClient();
        Assert.eq("https://chat.example.org", client.endpoint().baseUrl());
        Assert.eq("connect.sid=abc", client.sessionCookie());
    }

    public void testPasswortErscheintNichtInToString() {
        AnkaiConnection connection = new AnkaiConnection("chat.example.org", "anka", "streng-geheim");
        Assert.isTrue("kein Passwort in toString", !(connection.toString().contains("streng-geheim")));
        Assert.isTrue("URL sichtbar", connection.toString().contains("chat.example.org"));
    }

    public void testPasswortUndCookieStehenNichtImKlartextSchluessel() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "geheim"));
        for (String key : secrets.values.keySet()) {
            Assert.isTrue("Schluessel ohne Geheimnis: " + key, !(key.contains("geheim")));
        }
    }

    public void testSondertzeichenUeberlebenDieAblage() {
        MemoryStore secrets = new MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "päss:wort\nmit=zeichen"));
        Assert.eq("päss:wort\nmit=zeichen", store.load().password);
    }
}
