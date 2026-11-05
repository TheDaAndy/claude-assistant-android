package dev.claude.assistant.notifications;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import dev.claude.assistant.*;
import dev.claude.assistant.ankai.*;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

/** Explicit, non-exported tap target. Never carries credentials to the browser. */
public final class NotificationOpenActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AnkaiConnection account = EncryptedPrefsSecretStore.connectionStore(this).load();
        Intent intent = getIntent();
        if (account == null || !account.baseUrl.equals(intent.getStringExtra("base"))
                || !account.username.equals(intent.getStringExtra("user"))) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Uri target = Uri.parse(AnkaiNotification.safeTarget(account.baseUrl, intent.getStringExtra("target")));
            if ("personal".equals(target.getFragment())) startActivity(new Intent(this, DecisionsActivity.class));
            else try { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
            catch (ActivityNotFoundException unavailable) { startActivity(new Intent(this, MainActivity.class)); }
        }
        finish();
    }
}
