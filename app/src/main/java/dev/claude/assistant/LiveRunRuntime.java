package dev.claude.assistant;

import android.content.Context;

import dev.claude.assistant.ankai.LiveRunCoordinator;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

/** Ein gemeinsamer Laufzustand fuer Voice-UI, Hintergrundservice und Overlays. */
public final class LiveRunRuntime {
    private static volatile LiveRunCoordinator coordinator;

    private LiveRunRuntime() {
    }

    public static LiveRunCoordinator coordinator(Context context) {
        LiveRunCoordinator current = coordinator;
        if (current != null) return current;
        synchronized (LiveRunRuntime.class) {
            if (coordinator == null) {
                Context app = context.getApplicationContext();
                coordinator = new LiveRunCoordinator(EncryptedPrefsSecretStore.activeRunStore(app));
            }
            return coordinator;
        }
    }
}
