package dev.claude.assistant.remote;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import dev.claude.assistant.ankai.DeviceCommandPolicy;

/** A remote request becomes an intent only after a local, visible confirmation. */
public final class DeviceActionActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String target = getIntent().getStringExtra("target");
        DeviceLinkStore store = new DeviceLinkStore(this);
        long expires = getIntent().getLongExtra("expiresAt", 0);
        if (!store.enabled() || !DeviceCommandPolicy.allowed("open_settings", target, expires, System.currentTimeMillis())) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Einstellungen öffnen?")
                .setMessage("Ankaï-Projekt: " + store.projectName() + "\nSeite: " + target + "\nDie KI ändert keine Einstellung automatisch.")
                .setNegativeButton("Ablehnen", (d, w) -> finish())
                .setPositiveButton("Öffnen", (d, w) -> {
                    if (store.enabled() && expires > System.currentTimeMillis()) {
                        String action = Settings.ACTION_SETTINGS;
                        if ("wifi".equals(target)) action = Settings.ACTION_WIFI_SETTINGS;
                        if ("accessibility".equals(target)) action = Settings.ACTION_ACCESSIBILITY_SETTINGS;
                        if ("developer".equals(target)) action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS;
                        if ("application".equals(target)) action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                        Intent intent = new Intent(action);
                        if ("application".equals(target)) intent.setData(Uri.parse("package:" + getPackageName()));
                        try { startActivity(intent); } catch (android.content.ActivityNotFoundException unavailable) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    }
                    finish();
                }).setOnCancelListener(d -> finish()).show();
    }
}
