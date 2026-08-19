package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unveraenderlicher Zustand, den MainActivity darstellen kann. */
public final class ConnectionUiState {
    public final boolean connected;
    public final boolean busy;
    public final String baseUrl;
    public final String username;
    public final String defaultProjectId;
    public final List<AnkaiProject> projects;
    public final String error;

    ConnectionUiState(boolean connected, boolean busy, String baseUrl, String username,
                      String defaultProjectId, List<AnkaiProject> projects, String error) {
        this.connected = connected;
        this.busy = busy;
        this.baseUrl = baseUrl;
        this.username = username;
        this.defaultProjectId = defaultProjectId;
        this.projects = Collections.unmodifiableList(new ArrayList<>(projects));
        this.error = error;
    }

    static ConnectionUiState disconnected(String error) {
        return new ConnectionUiState(false, false, null, null, null, Collections.emptyList(), error);
    }
}
