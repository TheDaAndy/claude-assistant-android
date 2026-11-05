package dev.claude.assistant.notifications;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import dev.claude.assistant.MainActivity;
import dev.claude.assistant.R;
import dev.claude.assistant.ankai.AnkaiAuthException;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;
import java.util.concurrent.*;

/** Explicitly enabled inbox sync, independent of the remote-control channel. No wake lock. */
public final class NotificationService extends Service {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private boolean started;
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("ankai_notification_sync", "Ankaï-Verbindung", NotificationManager.IMPORTANCE_LOW));
        PendingIntent settings = PendingIntent.getActivity(this, 8513, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(8513, new Notification.Builder(this, "ankai_notification_sync")
                .setSmallIcon(R.drawable.ic_assistant).setContentTitle("Ankaï-Benachrichtigungen aktiv")
                .setContentText("Neue Meldungen werden auf diesem Handy angezeigt.")
                .setContentIntent(settings).setOngoing(true).build());
        if (!NotificationSync.enabled(this) || EncryptedPrefsSecretStore.connectionStore(this).load() == null) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            worker.scheduleWithFixedDelay(() -> {
                if (!NotificationSync.enabled(this) || EncryptedPrefsSecretStore.connectionStore(this).load() == null) { stopSelf(); return; }
                try { NotificationSync.sync(this); }
                catch (AnkaiAuthException rejected) { stopSelf(); }
                catch (Exception offline) { /* Keep receipts unchanged; retry after a bounded delay. */ }
            }, 0, 30, TimeUnit.SECONDS);
        }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdownNow(); stopForeground(true); super.onDestroy(); }
}
