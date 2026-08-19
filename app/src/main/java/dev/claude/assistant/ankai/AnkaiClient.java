package dev.claude.assistant.ankai;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HTTP-Client fuer eine verknuepfte Ankai-Instanz.
 *
 * Bewusst frei von android.*-Abhaengigkeiten, damit die Anbindung ohne Android-SDK
 * getestet werden kann. Zugangsdaten werden nur fuer den Authorization-Header
 * verwendet und niemals geloggt oder in toString ausgegeben.
 */
public final class AnkaiClient {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 300000;

    private final AnkaiEndpoint endpoint;
    private final String username;
    private final String password;
    private volatile String sessionCookie;

    public AnkaiClient(AnkaiEndpoint endpoint, String username, String password) {
        this.endpoint = endpoint;
        this.username = username;
        this.password = password;
    }

    public AnkaiEndpoint endpoint() {
        return endpoint;
    }

    public String sessionCookie() {
        return sessionCookie;
    }

    /** Uebernimmt ein zuvor gespeichertes Sessioncookie, um eine erneute Anmeldung zu sparen. */
    public void useSessionCookie(String cookie) {
        this.sessionCookie = cookie == null || cookie.trim().isEmpty() ? null : cookie.trim();
    }

    /** Prueft die Verknuepfung und liefert den angemeldeten Benutzernamen. */
    public String verifyConnection() throws IOException {
        HttpURLConnection connection = open("/api/auth/session", "GET");
        try {
            Map<String, Object> json = readJson(connection);
            Map<String, Object> user = AnkaiJson.object(json, "user");
            String name = user != null ? AnkaiJson.string(user, "username") : AnkaiJson.string(json, "username");
            if (name == null) throw new AnkaiAuthException("Ankai lieferte keinen angemeldeten Benutzer");
            return name;
        } finally {
            connection.disconnect();
        }
    }

    /** Projekte fuer die Auswahl des Default-Projekts. */
    public List<AnkaiProject> listProjects() throws IOException {
        HttpURLConnection connection = open("/api/projects", "GET");
        try {
            Map<String, Object> json = readJson(connection);
            List<AnkaiProject> projects = new ArrayList<>();
            for (Object entry : AnkaiJson.list(json, "projects")) {
                if (!(entry instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) entry;
                String id = AnkaiJson.string(item, "id");
                if (id != null) projects.add(new AnkaiProject(id, AnkaiJson.string(item, "name")));
            }
            return projects;
        } finally {
            connection.disconnect();
        }
    }

    /** Sendet eine Aufnahme und liefert den gestarteten sichtbaren Chatlauf. */
    public VoiceResult sendVoice(VoiceRequest request, VoiceProgressListener listener) throws IOException {
        HttpURLConnection connection = open("/api/voice", "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", request.contentType());
        connection.setRequestProperty("Accept", "application/x-ndjson");
        byte[] body = request.body();
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            captureCookie(connection);
            if (status == 401 || status == 403) {
                throw new AnkaiAuthException("Ankai-Verknuepfung ist abgelaufen");
            }
            if (status >= 400) {
                Map<String, Object> error = parseOrNull(readBody(errorStream(connection)));
                throw toError(error, "Ankai antwortete mit HTTP " + status);
            }
            return readVoiceStream(connection, listener);
        } finally {
            connection.disconnect();
        }
    }

    /** Trennt die Verknuepfung serverseitig und verwirft das Sessioncookie. */
    public void disconnect() {
        try {
            HttpURLConnection connection = open("/api/auth/logout", "POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(0);
            try (OutputStream out = connection.getOutputStream()) {
                out.flush();
            }
            connection.getResponseCode();
            connection.disconnect();
        } catch (IOException ignored) {
            // Auch bei Netzfehler wird die Verknuepfung lokal getrennt.
        } finally {
            sessionCookie = null;
        }
    }

    private VoiceResult readVoiceStream(HttpURLConnection connection, VoiceProgressListener listener)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            VoiceResult result = null;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Map<String, Object> event = parseOrNull(line);
                if (event == null) continue;
                String type = AnkaiJson.string(event, "type");
                if ("progress".equals(type)) {
                    if (listener != null) {
                        listener.onProgress(AnkaiJson.intValue(event, "percent", 0), AnkaiJson.string(event, "stage"));
                    }
                } else if ("error".equals(type)) {
                    throw toError(event, "Ankai meldete einen Fehler");
                } else if ("done".equals(type)) {
                    result = new VoiceResult(
                        AnkaiJson.string(event, "sessionId"),
                        AnkaiJson.string(event, "runId"),
                        AnkaiJson.string(event, "transcript"));
                } else if (event.containsKey("sessionId")) {
                    // Nicht streamende Antwort (JSON statt NDJSON).
                    result = new VoiceResult(
                        AnkaiJson.string(event, "sessionId"),
                        AnkaiJson.string(event, "runId"),
                        AnkaiJson.string(event, "transcript"));
                }
            }
            if (result == null) throw new AnkaiApiException("Ankai hat den Lauf nicht bestaetigt");
            return result;
        }
    }

    private AnkaiApiException toError(Map<String, Object> payload, String fallback) {
        String message = payload != null ? AnkaiJson.string(payload, "error") : null;
        if (message == null) message = fallback;
        String code = payload != null ? AnkaiJson.string(payload, "code") : null;
        if ("project_unknown".equals(code) || "project_ambiguous".equals(code)) {
            List<AnkaiProject> candidates = new ArrayList<>();
            for (Object entry : AnkaiJson.list(payload, "candidates")) {
                if (!(entry instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) entry;
                candidates.add(new AnkaiProject(AnkaiJson.string(item, "id"), AnkaiJson.string(item, "name")));
            }
            return new AnkaiRoutingException(message, code, candidates);
        }
        return new AnkaiApiException(message);
    }

    private HttpURLConnection open(String path, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.url(path)).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        if (sessionCookie != null) {
            connection.setRequestProperty("Cookie", sessionCookie);
        } else if (username != null) {
            String raw = username + ":" + (password == null ? "" : password);
            connection.setRequestProperty("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return connection;
    }

    private Map<String, Object> readJson(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        captureCookie(connection);
        if (status == 401 || status == 403) {
            sessionCookie = null;
            throw new AnkaiAuthException("Anmeldung an Ankai fehlgeschlagen");
        }
        if (status >= 400) {
            Map<String, Object> error = parseOrNull(readBody(errorStream(connection)));
            throw toError(error, "Ankai antwortete mit HTTP " + status);
        }
        Map<String, Object> json = parseOrNull(readBody(connection.getInputStream()));
        if (json == null) throw new AnkaiApiException("Ankai lieferte keine gueltige JSON-Antwort");
        return json;
    }

    private void captureCookie(HttpURLConnection connection) {
        List<String> headers = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && "set-cookie".equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null) {
                headers.addAll(entry.getValue());
            }
        }
        for (String header : headers) {
            int end = header.indexOf(';');
            String pair = end >= 0 ? header.substring(0, end) : header;
            if (pair.contains("=")) sessionCookie = pair;
        }
    }

    private static InputStream errorStream(HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        return stream != null ? stream : InputStream.nullInputStream();
    }

    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) buffer.write(chunk, 0, n);
        in.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parseOrNull(String json) {
        try {
            return AnkaiJson.parseObject(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "AnkaiClient{" + endpoint.baseUrl() + "}";
    }
}
