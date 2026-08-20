package dev.claude.assistant.ankai;

public final class ConnectionFormPolicyTest {
    public void testKeepsPasswordAfterFailedLogin() {
        ConnectionUiState failed = ConnectionUiState.disconnected("Anmeldung an Ankai fehlgeschlagen");

        Assert.isTrue("Passwort bleibt nach Fehler erhalten", !ConnectionFormPolicy.shouldClearPassword(failed));
    }

    public void testClearsPasswordAfterSuccessfulLogin() {
        ConnectionUiState connected = new ConnectionUiState(true, false, "https://ankai.example", "andy",
                null, java.util.Collections.emptyList(), null);

        Assert.isTrue("Passwort wird nach Erfolg entfernt", ConnectionFormPolicy.shouldClearPassword(connected));
    }
}
