package dev.claude.assistant.ankai;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Multipart-Anfrage fuer einen neuen textbasierten Ankai-Chat. */
public final class TextRequest {
    private final String boundary = "----ankai" + UUID.randomUUID().toString().replace("-", "");
    private final String message;
    private final String projectId;

    public TextRequest(String message, String projectId) {
        if (message == null || message.trim().isEmpty()) throw new IllegalArgumentException("Nachricht fehlt");
        this.message = message.trim();
        this.projectId = projectId == null || projectId.trim().isEmpty() ? null : projectId.trim();
    }

    public String contentType() { return "multipart/form-data; boundary=" + boundary; }

    public byte[] body() {
        StringBuilder body = new StringBuilder();
        field(body, "message", message);
        if (projectId != null) field(body, "projectId", projectId);
        body.append("--").append(boundary).append("--\r\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void field(StringBuilder body, String name, String value) {
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n");
    }
}
