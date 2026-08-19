package dev.claude.assistant.ankai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Multipart-Body fuer POST /api/voice.
 *
 * Routing wird genau dann angefordert, wenn es sich um eine neue Aufnahme ohne
 * explizit gewaehltes Projekt handelt. Eine Folgeaufnahme (sessionId) oder ein
 * explizites projectId umgehen das serverseitige Routing bewusst.
 */
public final class VoiceRequest {

    private final String boundary = "----ankai" + UUID.randomUUID().toString().replace("-", "");
    private final String filename;
    private final String audioContentType;
    private final byte[] audio;

    private String defaultProjectId;
    private String projectId;
    private String sessionId;
    private String language = "de";

    public VoiceRequest(String filename, String audioContentType, byte[] audio) {
        if (filename == null || filename.isEmpty() || filename.indexOf('"') >= 0
                || filename.indexOf('\r') >= 0 || filename.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Ungueltiger Dateiname");
        }
        this.filename = filename;
        this.audioContentType = audioContentType;
        this.audio = audio;
    }

    public void setDefaultProjectId(String value) {
        this.defaultProjectId = value;
    }

    public void setProjectId(String value) {
        this.projectId = value;
    }

    public void setSessionId(String value) {
        this.sessionId = value;
    }

    public void setLanguage(String value) {
        this.language = value;
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /** true, wenn Ankai das Zielprojekt aus dem Gesprochenen ableiten soll. */
    public boolean wantsRouting() {
        return isBlank(sessionId) && isBlank(projectId);
    }

    public byte[] body() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (wantsRouting()) {
                writeField(out, "routeProject", "1");
                if (!isBlank(defaultProjectId)) writeField(out, "defaultProjectId", defaultProjectId.trim());
            } else {
                if (!isBlank(sessionId)) writeField(out, "sessionId", sessionId.trim());
                if (!isBlank(projectId)) writeField(out, "projectId", projectId.trim());
            }
            if (!isBlank(language)) writeField(out, "language", language.trim());
            writeFile(out, audio);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Multipart-Body konnte nicht gebaut werden", e);
        }
    }

    private void writeField(ByteArrayOutputStream out, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
            + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeFile(ByteArrayOutputStream out, byte[] content) throws IOException {
        out.write(("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
            + "Content-Type: " + audioContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
