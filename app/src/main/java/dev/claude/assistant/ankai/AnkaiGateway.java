package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.List;

/** Schmale, testbare Schnittstelle fuer die von der Verknuepfungs-UI benoetigten API-Aufrufe. */
public interface AnkaiGateway {
    String verifyConnection() throws IOException;
    List<AnkaiProject> listProjects() throws IOException;
    String sessionCookie();
    void disconnect() throws IOException;
}
