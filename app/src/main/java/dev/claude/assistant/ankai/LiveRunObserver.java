package dev.claude.assistant.ankai;

/** Empfaengt vollstaendige, thread-sichere UI-Snapshots eines Laufs. */
public interface LiveRunObserver {
    void onChanged(LiveRunSnapshot snapshot);
}
