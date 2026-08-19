package dev.claude.assistant.ankai;

/** Beobachtet, welcher Lauf zuletzt neu in der prozessweiten Registry gestartet wurde. */
public interface LiveRunRegistryObserver {
    void onLatestRun(LiveRunState run);
}
