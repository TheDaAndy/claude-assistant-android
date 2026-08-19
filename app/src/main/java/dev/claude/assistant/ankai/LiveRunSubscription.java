package dev.claude.assistant.ankai;

/** Abmeldung eines Overlays, ohne den zugehoerigen Hintergrundlauf zu beenden. */
public interface LiveRunSubscription extends AutoCloseable {
    @Override
    void close();
}
