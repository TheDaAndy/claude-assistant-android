package dev.claude.assistant.ankai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ActiveRunStoreTest {

    public void testPersistsParallelRunsAndRestoresRegistryAfterRestart() {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore firstProcess = new ActiveRunStore(secrets);

        firstProcess.remember(" session-1 ", "run-1");
        firstProcess.remember("session-2", "run-2");

        ActiveRunStore restarted = new ActiveRunStore(secrets);
        List<ActiveRun> restored = restarted.load();
        assertEquals(2, restored.size());
        assertEquals("session-1", restored.get(0).sessionId());
        assertEquals("run-1", restored.get(0).runId());
        assertEquals("session-2", restored.get(1).sessionId());

        LiveRunRegistry registry = restarted.restoreRegistry();
        assertEquals(2, registry.size());
        assertEquals("run-2", registry.get("session-2").runId());
    }

    public void testRememberUpdatesExistingSessionWithoutDuplicate() {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);

        store.remember("session-1", "run-old");
        store.remember("session-1", "run-new");

        assertEquals(1, store.load().size());
        assertEquals("run-new", store.load().get(0).runId());
    }

    public void testForgetOnlyRemovesSelectedRunAndClearsEmptyStorage() {
        MemorySecretStore secrets = new MemorySecretStore();
        ActiveRunStore store = new ActiveRunStore(secrets);
        store.remember("session-1", "run-1");
        store.remember("session-2", null);

        assertTrue(store.forget("session-1"));
        assertEquals(1, store.load().size());
        assertFalse(store.forget("missing"));
        assertTrue(store.forget("session-2"));
        assertEquals(null, secrets.get(ActiveRunStore.STORAGE_KEY));
    }

    public void testMalformedEntriesAreIgnoredAndNeverEscapeStorageValue() {
        MemorySecretStore secrets = new MemorySecretStore();
        secrets.put(ActiveRunStore.STORAGE_KEY, "broken\nczE=\tdjE=\n!!!\tdjI=");

        List<ActiveRun> restored = new ActiveRunStore(secrets).load();

        assertEquals(1, restored.size());
        assertEquals("s1", restored.get(0).sessionId());
        assertEquals("v1", restored.get(0).runId());
    }

    private static final class MemorySecretStore implements SecretStore {
        private final Map<String, String> values = new HashMap<>();
        public String get(String key) { return values.get(key); }
        public void put(String key, String value) { values.put(key, value); }
        public void remove(String key) { values.remove(key); }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Erwartet: true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Erwartet: false");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Erwartet " + expected + ", war " + actual);
        }
    }
}
