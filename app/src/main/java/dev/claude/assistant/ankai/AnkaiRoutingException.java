package dev.claude.assistant.ankai;

import java.util.Collections;
import java.util.List;

/**
 * Ankai konnte das gesprochene Projekt nicht eindeutig aufloesen
 * ({@code project_unknown} oder {@code project_ambiguous}).
 * Die App zeigt die Kandidaten an und raet nicht selbst.
 */
public class AnkaiRoutingException extends AnkaiApiException {

    public final String code;
    public final List<AnkaiProject> candidates;

    public AnkaiRoutingException(String message, String code, List<AnkaiProject> candidates) {
        super(message);
        this.code = code;
        this.candidates = candidates == null ? Collections.emptyList() : Collections.unmodifiableList(candidates);
    }
}
