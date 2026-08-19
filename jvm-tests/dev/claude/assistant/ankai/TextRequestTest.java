package dev.claude.assistant.ankai;

import java.nio.charset.StandardCharsets;

public class TextRequestTest {
    public void testRejectsBlankMessage() {
        try {
            new TextRequest("  ", null);
            Assert.fail("Leere Nachricht muss abgewiesen werden");
        } catch (IllegalArgumentException expected) {}
    }

    public void testBuildsMessageAndOptionalProject() {
        TextRequest request = new TextRequest("  Hallo Ankai  ", " project-1 ");
        String body = new String(request.body(), StandardCharsets.UTF_8);
        Assert.isTrue("Nachricht fehlt", body.contains("Hallo Ankai"));
        Assert.isTrue("Projekt fehlt", body.contains("project-1"));
        Assert.isTrue("Multipart-Abschluss fehlt", body.endsWith("--\r\n"));
    }
}
