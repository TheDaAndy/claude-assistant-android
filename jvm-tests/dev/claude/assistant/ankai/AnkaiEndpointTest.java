package dev.claude.assistant.ankai;

public class AnkaiEndpointTest {

    public void testKeepsExplicitSchemeAndStripsTrailingSlashes() {
        Assert.eq("https://ankai.example.com", new AnkaiEndpoint("https://ankai.example.com//").baseUrl());
    }

    public void testAddsHttpsWhenSchemeMissing() {
        Assert.eq("https://ankai.example.com", new AnkaiEndpoint("ankai.example.com").baseUrl());
    }

    public void testKeepsPlainHttpForLocalDevelopment() {
        Assert.eq("http://127.0.0.1:3211", new AnkaiEndpoint("http://127.0.0.1:3211").baseUrl());
    }

    public void testTrimsSurroundingWhitespace() {
        Assert.eq("https://ankai.example.com", new AnkaiEndpoint("  ankai.example.com  ").baseUrl());
    }

    public void testBuildsApiUrls() {
        AnkaiEndpoint e = new AnkaiEndpoint("https://a.example");
        Assert.eq("https://a.example/api/voice", e.url("/api/voice"));
        Assert.eq("https://a.example/api/projects", e.url("api/projects"));
    }

    public void testRejectsEmptyHost() {
        try {
            new AnkaiEndpoint("   ");
            Assert.fail("leere Instanz-URL muss abgelehnt werden");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testToStringDoesNotLeakAnythingBeyondBaseUrl() {
        Assert.eq("https://a.example", new AnkaiEndpoint("https://a.example").toString());
    }
}
