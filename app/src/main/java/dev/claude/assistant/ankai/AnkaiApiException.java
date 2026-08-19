package dev.claude.assistant.ankai;

import java.io.IOException;

/** Fachlicher Fehler der Ankai-API. */
public class AnkaiApiException extends IOException {
    public AnkaiApiException(String message) {
        super(message);
    }
}
