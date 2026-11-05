package dev.claude.assistant.ankai;

import java.net.URI;
import java.util.*;

/** Server inbox item, independent of Android and of device-control grants. */
public final class AnkaiNotification {
    public final String id, title, body, url;
    public final boolean unread;
    private AnkaiNotification(Map<String,Object> row) {
        id = AnkaiJson.string(row, "id");
        String heading = AnkaiJson.string(row, "title");
        title = heading == null || heading.trim().isEmpty() ? "Ankaï" : heading;
        String text = AnkaiJson.string(row, "body"); body = text == null ? "" : text;
        url = AnkaiJson.string(row, "url");
        unread = Boolean.TRUE.equals(row.get("unread")) && row.get("readAt") == null;
    }
    public static List<AnkaiNotification> parse(Map<String,Object> json) {
        if (!(json.get("notifications") instanceof List)) throw new IllegalArgumentException("Benachrichtigungsliste fehlt");
        List<AnkaiNotification> result = new ArrayList<>();
        for (Object row : AnkaiJson.list(json, "notifications")) {
            if (!(row instanceof Map)) continue;
            @SuppressWarnings("unchecked") AnkaiNotification item = new AnkaiNotification((Map<String,Object>)row);
            if (item.id != null && item.id.matches("[A-Za-z0-9_-]{1,160}")) result.add(item);
        }
        return result;
    }
    /** Never launch a server-supplied external URL, intent or another account's origin. */
    public static String safeTarget(String base, String target) {
        try {
            URI origin = URI.create(base + "/");
            URI resolved = origin.resolve(target == null || target.trim().isEmpty() ? "./" : target);
            if (("https".equals(resolved.getScheme()) || "http".equals(resolved.getScheme()))
                    && Objects.equals(origin.getScheme(), resolved.getScheme())
                    && Objects.equals(origin.getHost(), resolved.getHost())
                    && origin.getPort() == resolved.getPort() && resolved.getUserInfo() == null)
                return resolved.toString();
        } catch (IllegalArgumentException ignored) { }
        return base + "/";
    }
}
