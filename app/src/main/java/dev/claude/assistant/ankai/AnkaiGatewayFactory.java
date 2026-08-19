package dev.claude.assistant.ankai;

/** Erzeugt ein Gateway fuer eine Verknuepfung; in Tests ohne echtes Netzwerk ersetzbar. */
public interface AnkaiGatewayFactory {
    AnkaiGateway create(AnkaiConnection connection);
}
