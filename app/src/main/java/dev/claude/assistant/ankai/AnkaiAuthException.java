package dev.claude.assistant.ankai;

/** Die Verknuepfung ist ungueltig oder abgelaufen; der Nutzer muss sich neu anmelden. */
public class AnkaiAuthException extends AnkaiApiException {
    public AnkaiAuthException(String message) {
        super(message);
    }
}
