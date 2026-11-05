package dev.claude.assistant.notifications;

import android.app.*;
import android.app.job.*;
import android.content.*;
import dev.claude.assistant.*;
import dev.claude.assistant.ankai.*;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;
import java.util.*;

public final class NotificationSync {
    static final String CHANNEL = "ankai_messages";
    private static final String GROUP = "ankai_inbox";
    private static final int JOB = 8513;
    private static final Object LOCK = new Object();
    private NotificationSync() { }
    public static boolean enabled(Context context) {
        return context.getSharedPreferences("notification_settings", 0).getBoolean("enabled", false);
    }
    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("notification_settings", 0).edit().putBoolean("enabled", enabled).apply();
        if (enabled) resume(context);
        else {
            context.getSystemService(JobScheduler.class).cancel(JOB);
            context.stopService(new Intent(context, NotificationService.class));
            clearDisplayed(context);
        }
    }
    public static void resume(Context context) {
        if (!enabled(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(JOB) == null) jobs.schedule(new JobInfo.Builder(JOB,
                new ComponentName(context, NotificationRecoveryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setPeriodic(15 * 60 * 1000L).build());
        if (EncryptedPrefsSecretStore.connectionStore(context).load() == null) return;
        try { context.startForegroundService(new Intent(context, NotificationService.class)); }
        catch (IllegalStateException | SecurityException restricted) { /* Periodic job remains available. */ }
    }
    public static void clearDisplayed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications())
            if (GROUP.equals(item.getNotification().getGroup())) manager.cancel(item.getTag(), item.getId());
    }
    /** One bounded request; callers run off the UI thread. */
    public static void sync(Context context) throws Exception {
        synchronized (LOCK) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (!enabled(context) || !manager.areNotificationsEnabled()) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Ankaï-Benachrichtigungen", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Neue Nachrichten und Hinweise aus deinem Ankaï-Konto");
            manager.createNotificationChannel(channel);
            if (manager.getNotificationChannel(CHANNEL).getImportance() == NotificationManager.IMPORTANCE_NONE) return;
            AnkaiConnectionStore store = EncryptedPrefsSecretStore.connectionStore(context);
            AnkaiConnection account = store.load();
            if (account == null) { clearDisplayed(context); return; }
            AnkaiClient client = account.newClient();
            List<AnkaiNotification> items = client.listNotifications();
            AnkaiConnection current = store.load();
            if (!enabled(context) || current == null || !account.baseUrl.equals(current.baseUrl)
                    || !account.username.equals(current.username) || !account.password.equals(current.password)) return;
            store.saveSessionCookie(client.sessionCookie());
            NotificationLedger ledger = new NotificationLedger(new EncryptedPrefsSecretStore(context), account.baseUrl + "\n" + account.username);
            Collections.reverse(items); // Oldest first; newest remains on top in Android.
            for (AnkaiNotification item : items) {
                if (!enabled(context) || !manager.areNotificationsEnabled()) return;
                if (!item.unread) { manager.cancel(item.id, 0); continue; }
                if (!ledger.shouldDeliver(item)) continue;
                Intent open = new Intent(context, NotificationOpenActivity.class)
                        .setData(android.net.Uri.parse("ankai-notification:" + item.id))
                        .putExtra("base", account.baseUrl).putExtra("user", account.username)
                        .putExtra("target", AnkaiNotification.safeTarget(account.baseUrl, item.url));
                PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notification = new Notification.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_assistant).setContentTitle(item.title).setContentText(item.body)
                        .setStyle(new Notification.BigTextStyle().bigText(item.body)).setContentIntent(tap)
                        .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setGroup(GROUP).build();
                manager.notify(item.id, 0, notification);
                ledger.delivered(item.id);
            }
        }
    }
}
