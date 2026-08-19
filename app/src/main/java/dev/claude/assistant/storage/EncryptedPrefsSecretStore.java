package dev.claude.assistant.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import dev.claude.assistant.ankai.AnkaiConnectionStore;
import dev.claude.assistant.ankai.SecretStore;

/**
 * Ablage der Ankai-Zugangsdaten in EncryptedSharedPreferences.
 *
 * Der Schluessel liegt im Android Keystore; Werte werden nie im Klartext
 * auf die Platte geschrieben und niemals nach Logcat ausgegeben.
 */
public final class EncryptedPrefsSecretStore implements SecretStore {

    private static final String FILE_NAME = "ankai_connection";

    private final SharedPreferences prefs;

    public EncryptedPrefsSecretStore(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            this.prefs = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Verschluesselte Ablage nicht verfuegbar", e);
        }
    }

    /** Bequemer Einstieg: fertiger Store fuer die App. */
    public static AnkaiConnectionStore connectionStore(Context context) {
        return new AnkaiConnectionStore(new EncryptedPrefsSecretStore(context));
    }

    @Override
    public String get(String key) {
        return prefs.getString(key, null);
    }

    @Override
    public void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}
