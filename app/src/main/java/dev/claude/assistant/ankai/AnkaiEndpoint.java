package dev.claude.assistant.ankai;

/** Zielinstanz einer Ankai-Verknuepfung. Normalisiert die vom Nutzer eingegebene Adresse. */
public final class AnkaiEndpoint {

    private final String baseUrl;

    public AnkaiEndpoint(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            if (value.isEmpty()) throw new IllegalArgumentException("Instanz-URL fehlt");
            value = "https://" + value;
        }
        String host = value.substring(value.indexOf("//") + 2);
        if (host.isEmpty()) throw new IllegalArgumentException("Instanz-URL fehlt");
        this.baseUrl = value;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String url(String path) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        return baseUrl + suffix;
    }

    @Override
    public String toString() {
        return baseUrl;
    }
}
