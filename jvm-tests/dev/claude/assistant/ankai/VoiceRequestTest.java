package dev.claude.assistant.ankai;

import java.nio.charset.StandardCharsets;

public class VoiceRequestTest {

    private String bodyOf(VoiceRequest request) {
        return new String(request.body(), StandardCharsets.ISO_8859_1);
    }

    public void testSendsRouteProjectFlagByDefault() {
        VoiceRequest r = new VoiceRequest("aufnahme.m4a", "audio/mp4", new byte[]{1, 2, 3});
        Assert.isTrue("routeProject muss gesetzt sein", bodyOf(r).contains("name=\"routeProject\"\r\n\r\n1"));
    }

    public void testIncludesDefaultProjectWhenConfigured() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        r.setDefaultProjectId("proj-1");
        Assert.isTrue("defaultProjectId fehlt", bodyOf(r).contains("name=\"defaultProjectId\"\r\n\r\nproj-1"));
    }

    public void testOmitsEmptyOptionalFields() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        r.setDefaultProjectId("  ");
        r.setSessionId(null);
        String body = bodyOf(r);
        Assert.isTrue("leeres defaultProjectId darf nicht gesendet werden", !body.contains("defaultProjectId"));
        Assert.isTrue("leeres sessionId darf nicht gesendet werden", !body.contains("name=\"sessionId\""));
    }

    public void testFollowUpRecordingDisablesRouting() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        r.setSessionId("sess-1");
        String body = bodyOf(r);
        Assert.isTrue("sessionId fehlt", body.contains("name=\"sessionId\"\r\n\r\nsess-1"));
        Assert.isTrue("Folgeaufnahme darf kein Routing anfordern", !body.contains("name=\"routeProject\""));
    }

    public void testExplicitProjectDisablesRouting() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        r.setProjectId("proj-9");
        String body = bodyOf(r);
        Assert.isTrue("projectId fehlt", body.contains("name=\"projectId\"\r\n\r\nproj-9"));
        Assert.isTrue("explizites Projekt braucht kein Routing", !body.contains("name=\"routeProject\""));
    }

    public void testDefaultsToGermanLanguage() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        Assert.isTrue("Sprache de fehlt", bodyOf(r).contains("name=\"language\"\r\n\r\nde"));
    }

    public void testCarriesAudioBytesAndFilename() {
        byte[] audio = new byte[]{9, 8, 7};
        VoiceRequest r = new VoiceRequest("aufnahme.m4a", "audio/mp4", audio);
        String body = bodyOf(r);
        Assert.isTrue("Dateiname fehlt", body.contains("filename=\"aufnahme.m4a\""));
        Assert.isTrue("Content-Type fehlt", body.contains("Content-Type: audio/mp4"));
        Assert.isTrue("Audiobytes fehlen", body.contains(new String(audio, StandardCharsets.ISO_8859_1)));
    }

    public void testContentTypeHeaderMatchesBoundaryAndBodyIsTerminated() {
        VoiceRequest r = new VoiceRequest("a.m4a", "audio/mp4", new byte[]{1});
        String boundary = r.contentType().substring(r.contentType().indexOf("boundary=") + 9);
        Assert.isTrue("Content-Type muss multipart sein", r.contentType().startsWith("multipart/form-data; boundary="));
        Assert.isTrue("Body muss mit Schlussboundary enden", bodyOf(r).endsWith("--" + boundary + "--\r\n"));
    }

    public void testRejectsSuspiciousFilename() {
        try {
            new VoiceRequest("a\"b.m4a", "audio/mp4", new byte[]{1});
            Assert.fail("Anfuehrungszeichen im Dateinamen muessen abgelehnt werden");
        } catch (IllegalArgumentException expected) {
        }
    }
}
