package dev.claude.assistant.ankai;

import java.io.IOException;
import java.util.List;

/** Produktiver Gateway-Adapter auf den bestehenden {@link AnkaiClient}. */
public final class AnkaiClientGateway implements AnkaiGateway {
    private final AnkaiClient client;

    public AnkaiClientGateway(AnkaiConnection connection) {
        this.client = connection.newClient();
    }

    @Override public String verifyConnection() throws IOException { return client.verifyConnection(); }
    @Override public List<AnkaiProject> listProjects() throws IOException { return client.listProjects(); }
    @Override public String sessionCookie() { return client.sessionCookie(); }
    @Override public void disconnect() { client.disconnect(); }
}
