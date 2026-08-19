package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Tests fuer die Android-freie Zustandslogik der Verknuepfungs-UI. */
public final class ConnectionPresenterTest {

    static final class FakeGateway implements AnkaiGateway {
        String verifiedUser = "anka";
        IOException verifyFailure;
        IOException listFailure;
        IOException disconnectFailure;
        String cookie = "connect.sid=neu";
        List<AnkaiProject> projects = Arrays.asList(
                new AnkaiProject("proj-1", "Ankai assistant"),
                new AnkaiProject("proj-2", "Privat"));
        boolean disconnected;

        @Override public String verifyConnection() throws IOException {
            if (verifyFailure != null) throw verifyFailure;
            return verifiedUser;
        }
        @Override public List<AnkaiProject> listProjects() throws IOException {
            if (listFailure != null) throw listFailure;
            return projects;
        }
        @Override public String sessionCookie() { return cookie; }
        @Override public void disconnect() throws IOException {
            disconnected = true;
            if (disconnectFailure != null) throw disconnectFailure;
        }
    }

    static final class FakeFactory implements AnkaiGatewayFactory {
        final FakeGateway gateway = new FakeGateway();
        AnkaiConnection connection;
        @Override public AnkaiGateway create(AnkaiConnection connection) {
            this.connection = connection;
            return gateway;
        }
    }

    public void testInitialStateIstGetrennt() {
        ConnectionPresenter presenter = presenter(new AnkaiConnectionStoreTest.MemoryStore(), new FakeFactory());
        ConnectionUiState state = presenter.state();
        Assert.isTrue("getrennt", !state.connected);
        Assert.isTrue("nicht beschaeftigt", !state.busy);
        Assert.eq(0, state.projects.size());
    }

    public void testConnectValidiertVorNetzwerkzugriff() {
        FakeFactory factory = new FakeFactory();
        ConnectionPresenter presenter = presenter(new AnkaiConnectionStoreTest.MemoryStore(), factory);

        ConnectionUiState state = presenter.connect(" ", "anka", "pw");

        Assert.isTrue("Fehler sichtbar", state.error != null && !state.error.isEmpty());
        Assert.isTrue("kein Gateway", factory.connection == null);
    }

    public void testConnectPrueftLoginUndLaedtProjekte() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        FakeFactory factory = new FakeFactory();
        ConnectionPresenter presenter = presenter(secrets, factory);

        ConnectionUiState state = presenter.connect("chat.example.org", " anka ", "pw");

        Assert.isTrue("verbunden", state.connected);
        Assert.eq("anka", state.username);
        Assert.eq("https://chat.example.org", state.baseUrl);
        Assert.eq(2, state.projects.size());
        Assert.eq("connect.sid=neu", new AnkaiConnectionStore(secrets).load().sessionCookie);
        Assert.eq("anka", factory.connection.username);
    }

    public void testFalscheAnmeldungWirdNichtGespeichert() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        FakeFactory factory = new FakeFactory();
        factory.gateway.verifyFailure = new AnkaiAuthException("Anmeldung fehlgeschlagen");
        ConnectionPresenter presenter = presenter(secrets, factory);

        ConnectionUiState state = presenter.connect("chat.example.org", "anka", "falsch");

        Assert.isTrue("nicht verbunden", !state.connected);
        Assert.eq("Anmeldung fehlgeschlagen", state.error);
        Assert.isTrue("keine Zugangsdaten gespeichert", new AnkaiConnectionStore(secrets).load() == null);
    }

    public void testProjektlistenFehlerSpeichertKeineVerknuepfung() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        FakeFactory factory = new FakeFactory();
        factory.gateway.listFailure = new IOException("Projektliste nicht erreichbar");
        ConnectionPresenter presenter = presenter(secrets, factory);

        ConnectionUiState state = presenter.connect("chat.example.org", "anka", "pw");

        Assert.eq("Projektliste nicht erreichbar", state.error);
        Assert.isTrue("keine halbe Verknuepfung", new AnkaiConnectionStore(secrets).load() == null);
    }

    public void testDefaultProjektKannGesetztUndEntferntWerden() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        ConnectionPresenter presenter = presenter(secrets, new FakeFactory());
        presenter.connect("chat.example.org", "anka", "pw");

        ConnectionUiState selected = presenter.selectDefaultProject("proj-2");
        Assert.eq("proj-2", selected.defaultProjectId);
        Assert.eq("Privat", new AnkaiConnectionStore(secrets).load().defaultProjectName);

        ConnectionUiState cleared = presenter.selectDefaultProject(null);
        Assert.isTrue("Default geloescht", cleared.defaultProjectId == null);
    }

    public void testUnbekannteProjektauswahlWirdAbgelehnt() {
        ConnectionPresenter presenter = presenter(new AnkaiConnectionStoreTest.MemoryStore(), new FakeFactory());
        presenter.connect("chat.example.org", "anka", "pw");

        ConnectionUiState state = presenter.selectDefaultProject("proj-fremd");

        Assert.isTrue("Fehler sichtbar", state.error.contains("nicht verfügbar"));
        Assert.isTrue("kein Default", state.defaultProjectId == null);
    }

    public void testDisconnectLoeschtLokalAuchBeiServerfehler() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        FakeFactory factory = new FakeFactory();
        ConnectionPresenter presenter = presenter(secrets, factory);
        presenter.connect("chat.example.org", "anka", "pw");
        factory.gateway.disconnectFailure = new IOException("offline");

        ConnectionUiState state = presenter.disconnect();

        Assert.isTrue("Gateway aufgerufen", factory.gateway.disconnected);
        Assert.isTrue("lokal getrennt", !state.connected);
        Assert.isTrue("Ablage leer", new AnkaiConnectionStore(secrets).load() == null);
        Assert.isTrue("kein Fehler noetig", state.error == null);
    }

    public void testBestehendeVerknuepfungWirdBeimStartAngezeigt() {
        AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
        AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
        store.save(new AnkaiConnection("chat.example.org", "anka", "pw", "proj-1", "Ankai assistant", "sid=alt"));
        FakeFactory factory = new FakeFactory();
        factory.gateway.projects = Collections.singletonList(new AnkaiProject("proj-1", "Ankai assistant"));

        ConnectionUiState state = new ConnectionPresenter(store, factory).refresh();

        Assert.isTrue("verbunden", state.connected);
        Assert.eq("proj-1", state.defaultProjectId);
        Assert.eq(1, state.projects.size());
    }

    private static ConnectionPresenter presenter(SecretStore secrets, AnkaiGatewayFactory factory) {
        return new ConnectionPresenter(new AnkaiConnectionStore(secrets), factory);
    }
}
