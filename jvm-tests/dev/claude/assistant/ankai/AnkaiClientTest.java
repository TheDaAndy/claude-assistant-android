package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.List;

public class AnkaiClientTest {

    private AnkaiClient clientFor(FakeAnkai fake) {
        return new AnkaiClient(new AnkaiEndpoint(fake.baseUrl()), "anka", "geheim");
    }

    public void testVerifySessionReturnsUser() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/auth/session", 200, "application/json", "{\"user\":{\"username\":\"anka\"}}");
            Assert.eq("anka", clientFor(fake).verifyConnection());
            Assert.isTrue("Basic-Auth muss gesendet werden", fake.authHeaders.get(0).startsWith("Basic "));
        } finally {
            fake.stop();
        }
    }

    public void testWrongCredentialsRaiseAuthError() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/auth/session", 401, "application/json", "{\"error\":\"unauthorized\"}");
            clientFor(fake).verifyConnection();
            Assert.fail("401 muss AnkaiAuthException ausloesen");
        } catch (AnkaiAuthException expected) {
        } finally {
            fake.stop();
        }
    }

    public void testListProjectsMapsIdAndName() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/projects", 200, "application/json",
                "{\"projects\":[{\"id\":\"p1\",\"name\":\"Ankai assistant\"},{\"id\":\"p2\",\"name\":\"Garten\"}]}");
            List<AnkaiProject> projects = clientFor(fake).listProjects();
            Assert.eq(2, projects.size());
            Assert.eq("p1", projects.get(0).id);
            Assert.eq("Garten", projects.get(1).name);
        } finally {
            fake.stop();
        }
    }

    public void testVoiceStreamsProgressAndReturnsRun() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                "{\"type\":\"progress\",\"percent\":10,\"stage\":\"upload\"}\n"
                    + "{\"type\":\"progress\",\"percent\":80,\"stage\":\"transcribe\"}\n"
                    + "{\"type\":\"done\",\"sessionId\":\"s1\",\"runId\":\"r1\",\"transcript\":\"Hallo\"}\n");
            List<String> stages = new ArrayList<>();
            VoiceRequest request = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1, 2});
            request.setDefaultProjectId("p1");
            VoiceResult result = clientFor(fake).sendVoice(request, (percent, stage) -> stages.add(percent + ":" + stage));
            Assert.eq("s1", result.sessionId);
            Assert.eq("r1", result.runId);
            Assert.eq("Hallo", result.transcript);
            Assert.eq(2, stages.size());
            Assert.eq("10:upload", stages.get(0));
            Assert.isTrue("Routing muss angefordert werden", fake.lastBody.contains("name=\"routeProject\""));
            Assert.isTrue("Default-Projekt muss mitgehen", fake.lastBody.contains("p1"));
        } finally {
            fake.stop();
        }
    }

    public void testLiveRunStreamsAssistantAndDoneEvents() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/sessions/session-1/live", 200, "application/x-ndjson",
                "{\"type\":\"assistant\",\"text\":\"Zwischenstand\"}\n"
                    + "{\"type\":\"assistant\",\"text\":\"Fertige Antwort\"}\n"
                    + "{\"type\":\"done\"}\n");
            List<LiveRunEvent> events = new ArrayList<>();
            boolean active = clientFor(fake).streamLiveRun("session-1", events::add);
            Assert.isTrue("Live-Lauf muss erkannt werden", active);
            Assert.eq(3, events.size());
            Assert.eq("assistant", events.get(0).type);
            Assert.eq("Zwischenstand", events.get(0).text);
            Assert.eq("done", events.get(2).type);
            Assert.isTrue("Live-Endpunkt muss aufgerufen werden",
                fake.requestLog.contains("GET /api/sessions/session-1/live"));
        } finally {
            fake.stop();
        }
    }

    public void testLiveRunReturnsInactiveWithoutFakeEvent() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/sessions/session-2/live", 200, "application/json", "{\"active\":false}");
            List<LiveRunEvent> events = new ArrayList<>();
            boolean active = clientFor(fake).streamLiveRun("session-2", events::add);
            Assert.isTrue("beendeter Lauf darf nicht aktiv sein", !active);
            Assert.eq(0, events.size());
        } finally {
            fake.stop();
        }
    }

    public void testLiveRunRequiresSessionId() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            clientFor(fake).streamLiveRun("  ", event -> {});
            Assert.fail("Leere Session-ID muss abgewiesen werden");
        } catch (IllegalArgumentException expected) {
        } finally {
            fake.stop();
        }
    }

    public void testUnknownProjectRaisesRoutingErrorWithoutGuessing() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 409, "application/json",
                "{\"error\":\"Projekt unbekannt\",\"code\":\"project_unknown\",\"candidates\":[]}");
            clientFor(fake).sendVoice(new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1}), null);
            Assert.fail("409 muss AnkaiRoutingException ausloesen");
        } catch (AnkaiRoutingException e) {
            Assert.eq("project_unknown", e.code);
            Assert.eq(0, e.candidates.size());
        } finally {
            fake.stop();
        }
    }

    public void testAmbiguousProjectExposesCandidates() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 409, "application/json",
                "{\"error\":\"mehrdeutig\",\"code\":\"project_ambiguous\","
                    + "\"candidates\":[{\"id\":\"p1\",\"name\":\"Ankai assistant\"},{\"id\":\"p2\",\"name\":\"Ankai web\"}]}");
            clientFor(fake).sendVoice(new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1}), null);
            Assert.fail("409 muss AnkaiRoutingException ausloesen");
        } catch (AnkaiRoutingException e) {
            Assert.eq("project_ambiguous", e.code);
            Assert.eq(2, e.candidates.size());
            Assert.eq("Ankai web", e.candidates.get(1).name);
        } finally {
            fake.stop();
        }
    }

    public void testRoutingErrorInsideStreamIsRaisedToo() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                "{\"type\":\"progress\",\"percent\":5,\"stage\":\"upload\"}\n"
                    + "{\"type\":\"error\",\"error\":\"mehrdeutig\",\"code\":\"project_ambiguous\","
                    + "\"candidates\":[{\"id\":\"p1\",\"name\":\"Alpha\"}]}\n");
            clientFor(fake).sendVoice(new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1}), null);
            Assert.fail("error-Event muss AnkaiRoutingException ausloesen");
        } catch (AnkaiRoutingException e) {
            Assert.eq("project_ambiguous", e.code);
            Assert.eq("Alpha", e.candidates.get(0).name);
        } finally {
            fake.stop();
        }
    }

    public void testPlainStreamErrorWithoutCodeBecomesApiException() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                "{\"type\":\"error\",\"error\":\"Transkription fehlgeschlagen\",\"retry\":true}\n");
            clientFor(fake).sendVoice(new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1}), null);
            Assert.fail("error-Event muss eine Exception ausloesen");
        } catch (AnkaiRoutingException e) {
            Assert.fail("ohne code darf kein Routing-Fehler entstehen");
        } catch (AnkaiApiException e) {
            Assert.isTrue("Fehlertext durchreichen", e.getMessage().contains("Transkription"));
        } finally {
            fake.stop();
        }
    }

    public void testStreamWithoutDoneEventFails() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/voice", 200, "application/x-ndjson",
                "{\"type\":\"progress\",\"percent\":50,\"stage\":\"transcribe\"}\n");
            clientFor(fake).sendVoice(new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1}), null);
            Assert.fail("abgebrochener Stream muss auffallen");
        } catch (AnkaiApiException expected) {
        } finally {
            fake.stop();
        }
    }

    public void testSessionCookieIsReusedAfterFirstCall() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/auth/session", 200, "application/json", "{\"user\":{\"username\":\"anka\"}}");
            AnkaiClient client = clientFor(fake);
            client.verifyConnection();
            client.verifyConnection();
            Assert.eq("", fake.cookieHeaders.get(0));
            Assert.isTrue("Cookie muss wiederverwendet werden",
                fake.cookieHeaders.get(1).contains("ankai_session=abc123"));
        } finally {
            fake.stop();
        }
    }

    public void testDisconnectDropsCookieAndCallsLogout() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            fake.respond("/api/auth/session", 200, "application/json", "{\"user\":{\"username\":\"anka\"}}");
            fake.respond("/api/auth/logout", 200, "application/json", "{\"ok\":true}");
            AnkaiClient client = clientFor(fake);
            client.verifyConnection();
            client.disconnect();
            Assert.isTrue("Logout muss aufgerufen werden", fake.requestLog.contains("POST /api/auth/logout"));
            Assert.isTrue("Cookie muss verworfen sein", client.sessionCookie() == null);
        } finally {
            fake.stop();
        }
    }

    public void testCredentialsNeverAppearInToString() throws Exception {
        FakeAnkai fake = new FakeAnkai();
        try {
            String text = clientFor(fake).toString();
            Assert.isTrue("Passwort darf nicht sichtbar sein", !text.contains("geheim"));
        } finally {
            fake.stop();
        }
    }
}
