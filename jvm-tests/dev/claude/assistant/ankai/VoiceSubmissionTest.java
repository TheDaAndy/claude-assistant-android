package dev.claude.assistant.ankai;

/** Tests fuer die zentrale Uebergabe einer Aufnahme an die verknuepfte Ankai-Instanz. */
public final class VoiceSubmissionTest {

    public void testOhneVerknuepfungWirdNichtGesendet() throws Exception {
        VoiceSubmission submission = new VoiceSubmission(
                new AnkaiConnectionStore(new AnkaiConnectionStoreTest.MemoryStore()));

        boolean thrown = false;
        try {
            submission.submit("aufnahme.m4a", "audio/mp4", new byte[]{1}, null);
        } catch (AnkaiAuthException expected) {
            thrown = true;
        }
        Assert.isTrue("fehlende Verknuepfung wird gemeldet", thrown);
    }

    public void testDefaultProjektUndFortschrittWerdenWeitergereicht() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                    "{\"type\":\"progress\",\"percent\":35,\"stage\":\"transcribe\"}\n"
                    + "{\"type\":\"done\",\"sessionId\":\"sess-7\",\"runId\":\"run-8\","
                    + "\"transcript\":\"Hallo\"}\n");
            AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
            AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
            store.save(new AnkaiConnection(fake.baseUrl(), "anka", "geheim"));
            store.saveDefaultProject(new AnkaiProject("proj-default", "Standard"));
            int[] progress = {-1};

            VoiceResult result = new VoiceSubmission(store).submit(
                    "aufnahme.m4a", "audio/mp4", new byte[]{1, 2, 3},
                    (percent, stage) -> progress[0] = percent);

            Assert.eq("sess-7", result.sessionId);
            Assert.eq(35, progress[0]);
            Assert.isTrue("Routing aktiv", fake.lastBody.contains("name=\"routeProject\""));
            Assert.isTrue("Default gesendet", fake.lastBody.contains("proj-default"));
        } finally {
            fake.stop();
        }
    }

    public void testErneuertesCookieWirdNachSendenGespeichert() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                    "{\"type\":\"done\",\"sessionId\":\"s\",\"runId\":\"r\"}\n");
            AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
            AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
            store.save(new AnkaiConnection(fake.baseUrl(), "anka", "geheim"));

            new VoiceSubmission(store).submit("aufnahme.m4a", "audio/mp4", new byte[]{1}, null);

            Assert.eq("ankai_session=abc123", store.load().sessionCookie);
        } finally {
            fake.stop();
        }
    }

    public void testRoutingFehlerBleibtFuerKandidatenauswahlErhalten() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 409, "application/json",
                    "{\"error\":\"Mehrdeutig\",\"code\":\"project_ambiguous\","
                    + "\"candidates\":[{\"id\":\"p1\",\"name\":\"Alpha\"}]}");
            AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
            AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
            store.save(new AnkaiConnection(fake.baseUrl(), "anka", "geheim"));

            try {
                new VoiceSubmission(store).submit("aufnahme.m4a", "audio/mp4", new byte[]{1}, null);
                Assert.fail("409 erwartet");
            } catch (AnkaiRoutingException expected) {
                Assert.eq("project_ambiguous", expected.code);
                Assert.eq("p1", expected.candidates.get(0).id);
            }
        } finally {
            fake.stop();
        }
    }

    public void testAusgewaehltesProjektWirdOhneErneutesRoutingGesendet() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                    "{\"type\":\"done\",\"sessionId\":\"s\",\"runId\":\"r\"}\n");
            AnkaiConnectionStoreTest.MemoryStore secrets = new AnkaiConnectionStoreTest.MemoryStore();
            AnkaiConnectionStore store = new AnkaiConnectionStore(secrets);
            store.save(new AnkaiConnection(fake.baseUrl(), "anka", "geheim"));

            new VoiceSubmission(store).submitToProject(
                    "aufnahme.m4a", "audio/mp4", new byte[]{1}, "projekt-2", null);

            Assert.isTrue("Projekt-ID gesendet", fake.lastBody.contains("projekt-2"));
            Assert.isTrue("explizites Projektfeld", fake.lastBody.contains("name=\"projectId\""));
            Assert.isTrue("kein erneutes Routing", !fake.lastBody.contains("name=\"routeProject\""));
        } finally {
            fake.stop();
        }
    }
}
