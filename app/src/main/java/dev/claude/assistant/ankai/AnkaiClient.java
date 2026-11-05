package dev.claude.assistant.ankai;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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
        HttpURLConnection connection = authenticatedGet("/api/auth/session", "application/json");
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
        HttpURLConnection connection = authenticatedGet("/api/projects", "application/json");
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

    public List<AnkaiNotification> listNotifications() throws IOException {
        HttpURLConnection connection = authenticatedGet("/api/notifications", "application/json", 20000);
        try { return AnkaiNotification.parse(readJson(connection)); }
        finally { connection.disconnect(); }
    }

    public PersonalDashboard loadPersonalDashboard() throws IOException {
        HttpURLConnection connection=authenticatedGet("/api/personal","application/json",20000);
        try { return PersonalDashboard.from(readJson(connection)); }
        finally { connection.disconnect(); }
    }

    public byte[] loadPersonalAttachment(String decision,int revision,PersonalAttachment attachment)throws IOException{
        if(revision<1)throw new IllegalArgumentException("Ungültige Revision");
        String path="/api/personal/decisions/"+decisionId(decision)+"/attachments/"+attachment.id+"?revision="+revision;
        HttpURLConnection connection=authenticatedGet(path,attachment.mimeType,20000);
        try{
            int status=connection.getResponseCode();
            if(status>=400)throw new IOException(status==409?"Vorschlag oder Anhang wurde geändert. Bitte aktualisieren.":"Anhang konnte nicht geladen werden ("+status+").");
            String type=connection.getContentType();
            if(type==null||!attachment.mimeType.equals(type.split(";")[0]))throw new IOException("Unerwarteter Dateityp.");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
            try(InputStream input=connection.getInputStream()){
                while((n=input.read(chunk))!=-1){if(buffer.size()+n>attachment.size)throw new IOException("Anhang überschreitet die freigegebene Größe.");buffer.write(chunk,0,n);}
            }
            byte[] bytes=buffer.toByteArray();attachment.verify(bytes);return bytes;
        }finally{connection.disconnect();}
    }

    public Map<String,Object> respondToPersonalDecision(String id,int revision,String response,String message,String option) throws IOException {
        String encoded=decisionId(id);
        if(revision<1 || !("approve".equals(response)||"reject".equals(response)||"revise".equals(response)))throw new IllegalArgumentException("Ungültige Entscheidung");
        String body="{\"revision\":"+revision+",\"response\":"+AnkaiJson.escape(response)+",\"message\":"+AnkaiJson.escape(message==null?"":message)+",\"option\":"+AnkaiJson.escape(option==null?"":option)+"}";
        return personalMutation("/api/personal/decisions/"+encoded+"/respond","application/json; charset=utf-8",body.getBytes(StandardCharsets.UTF_8));
    }

    public Map<String,Object> evaluatePersonalDecision(String id,boolean helpful) throws IOException {
        String path="/api/personal/decisions/"+decisionId(id)+"/evaluate";
        return personalMutation(path,"application/json; charset=utf-8",("{\"helpful\":"+helpful+"}").getBytes(StandardCharsets.UTF_8));
    }

    public Map<String,Object> sendPersonalDecisionVoice(String id,int revision,String filename,String type,byte[] audio) throws IOException {
        String path="/api/personal/decisions/"+decisionId(id)+"/voice";
        DecisionVoiceRequest request=new DecisionVoiceRequest(revision,filename,type,audio);
        return personalMutation(path,request.contentType(),request.body());
    }

    private Map<String,Object> personalMutation(String path,String type,byte[] body) throws IOException {
        // Renew authentication with a read BEFORE sending; never replay a mutation.
        verifyConnection();
        HttpURLConnection connection=open(path,"POST");
        connection.setDoOutput(true);connection.setRequestProperty("Content-Type",type);connection.setRequestProperty("Accept","application/json");
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            return readJson(connection);
        } finally { connection.disconnect(); }
    }

    private static String decisionId(String id){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,160}"))throw new IllegalArgumentException("Ungültige Entscheidungs-ID");
        return id;
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

    /** Startet einen textbasierten Chat und spiegelt dessen NDJSON-Ereignisse. */
    public String sendText(TextRequest request, LiveRunListener listener) throws IOException {
        HttpURLConnection connection = open("/api/chat", "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", request.contentType());
        connection.setRequestProperty("Accept", "application/x-ndjson");
        byte[] body = request.body();
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            captureCookie(connection);
            if (status == 401 || status == 403) throw new AnkaiAuthException("Ankai-Verknuepfung ist abgelaufen");
            if (status >= 400) throw toError(parseOrNull(readBody(errorStream(connection))),
                    "Ankai antwortete mit HTTP " + status);
            String sessionId = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Map<String, Object> payload = parseOrNull(line);
                    if (payload == null) continue;
                    String type = AnkaiJson.string(payload, "type");
                    String text = "session".equals(type) ? AnkaiJson.string(payload, "sessionId")
                            : AnkaiJson.string(payload, "text");
                    if ("session".equals(type)) sessionId = text;
                    if (type != null && listener != null) listener.onEvent(new LiveRunEvent(type, text));
                }
            }
            if (sessionId == null) throw new AnkaiApiException("Ankai hat den Chat nicht bestaetigt");
            return sessionId;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Verbindet sich mit dem Live-Stream eines sichtbaren Chatlaufs.
     *
     * @return true, wenn ein aktiver Stream geliefert wurde; false, wenn der Lauf bereits beendet ist.
     */
    public boolean streamLiveRun(String sessionId, LiveRunListener listener) throws IOException {
        String value = sessionId == null ? "" : sessionId.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Session-ID fehlt");
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        HttpURLConnection connection = authenticatedGet("/api/sessions/" + encoded + "/live", "application/x-ndjson");
        try {
            int status = connection.getResponseCode();
            captureCookie(connection);
            if (status == 401 || status == 403) {
                sessionCookie = null;
                throw new AnkaiAuthException("Ankai-Verknuepfung ist abgelaufen");
            }
            if (status >= 400) {
                Map<String, Object> error = parseOrNull(readBody(errorStream(connection)));
                throw toError(error, "Ankai antwortete mit HTTP " + status);
            }
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                Map<String, Object> payload = parseOrNull(readBody(connection.getInputStream()));
                return payload == null || !Boolean.FALSE.equals(payload.get("active"));
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    Map<String, Object> payload = parseOrNull(line);
                    if (payload == null) continue;
                    String type = AnkaiJson.string(payload, "type");
                    if (type != null && listener != null) {
                        listener.onEvent(new LiveRunEvent(type, AnkaiJson.string(payload, "text")));
                    }
                }
            }
            return true;
        } finally {
            connection.disconnect();
        }
    }

    /** Laedt bereits persistierte Assistant-Nachrichten fuer einen Reconnect. */
    public List<String> loadAssistantHistory(String sessionId) throws IOException {
        String value = sessionId == null ? "" : sessionId.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Session-ID fehlt");
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        HttpURLConnection connection = authenticatedGet("/api/sessions/" + encoded, "application/json");
        try {
            Map<String, Object> json = readJson(connection);
            Map<String, Object> session = AnkaiJson.object(json, "session");
            List<String> messages = new ArrayList<>();
            for (Object entry : AnkaiJson.list(session, "messages")) {
                if (!(entry instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) entry;
                if (!"assistant".equals(AnkaiJson.string(message, "role"))) continue;
                String content = AnkaiJson.string(message, "content");
                if (content != null && !content.trim().isEmpty()) messages.add(content.trim());
            }
            return messages;
        } finally {
            connection.disconnect();
        }
    }

    /** Erzeugt serverseitig kostenfreies Piper-TTS und liefert eine WAV-Datei. */
    public byte[] synthesizeSpeech(String text) throws IOException {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Text fehlt");
        HttpURLConnection connection = open("/api/tts", "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] body = ("{\"text\":\"" + escapeJson(value) + "\"}").getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            captureCookie(connection);
            if (status == 401 || status == 403) throw new AnkaiAuthException("Ankai-Verknuepfung ist abgelaufen");
            if (status >= 400) throw toError(parseOrNull(readBody(errorStream(connection))),
                    "Server-Sprachausgabe nicht verfuegbar (HTTP " + status + ")");
            return readBytes(connection.getInputStream());
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

    /** Retry only a rejected cookie on a read, once. Never replay a mutation/upload. */
    private HttpURLConnection authenticatedGet(String path, String accept) throws IOException {
        return authenticatedGet(path,accept,READ_TIMEOUT_MS);
    }

    private HttpURLConnection authenticatedGet(String path, String accept,int readTimeout) throws IOException {
        boolean hadCookie = sessionCookie != null;
        HttpURLConnection connection = open(path, "GET");
        connection.setRequestProperty("Accept", accept);
        connection.setReadTimeout(readTimeout);
        try {
            int status = connection.getResponseCode();
            if (hadCookie && username != null && (status == 401 || status == 403)) {
                connection.disconnect();
                sessionCookie = null;
                connection = open(path, "GET");
                connection.setRequestProperty("Accept", accept);
                connection.setReadTimeout(readTimeout);
            }
            return connection;
        } catch (IOException failed) {
            connection.disconnect();
            throw failed;
        }
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
        return new String(readBytes(in), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) buffer.write(chunk, 0, n);
        in.close();
        return buffer.toByteArray();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
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
