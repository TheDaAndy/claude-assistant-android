package dev.claude.assistant.ankai;

/** Entfernt interne Wrapper-Metadaten aus der im Android-Overlay sichtbaren Antwort. */
final class AssistantOutputCleaner {
    private AssistantOutputCleaner() {}

    static String clean(String value) {
        if (value == null) return null;
        StringBuilder visible = new StringBuilder();
        for (String line : value.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("ChatTitle:") || trimmed.startsWith("ProjectContext:")) continue;
            if (visible.length() > 0) visible.append('\n');
            visible.append(line);
        }
        return visible.toString().trim().replaceAll("\n{3,}", "\n\n");
    }
}
