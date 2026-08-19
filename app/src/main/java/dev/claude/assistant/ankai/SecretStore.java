package dev.claude.assistant.ankai;

/**
 * Schluessel/Wert-Ablage fuer Geheimnisse.
 *
 * Auf dem Geraet wird das von EncryptedSharedPreferences implementiert
 * (siehe dev.claude.assistant.storage.EncryptedPrefsSecretStore); die
 * Schnittstelle bleibt frei von android.*, damit die Logik testbar ist.
 */
public interface SecretStore {

    String get(String key);

    void put(String key, String value);

    void remove(String key);
}
