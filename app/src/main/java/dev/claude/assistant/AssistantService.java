package dev.claude.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.claude.assistant.ankai.AnkaiClient;
import dev.claude.assistant.ankai.AnkaiConnection;
import dev.claude.assistant.ankai.AnkaiConnectionStore;
import dev.claude.assistant.ankai.LiveRunCoordinator;
import dev.claude.assistant.ankai.LiveRunState;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

public class AssistantService extends Service {
    private static final String CHANNEL_ID = "claude_assistant_channel";
    private static final int NOTIFICATION_ID = 1;
    private final ExecutorService reconnectExecutor = Executors.newCachedThreadPool();
    private final Set<String> reconnectingSessions = ConcurrentHashMap.newKeySet();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        reconnectStoredRuns();
        return START_STICKY;
    }

    private void reconnectStoredRuns() {
        AnkaiConnectionStore connections = EncryptedPrefsSecretStore.connectionStore(this);
        AnkaiConnection connection = connections.load();
        if (connection == null) return;
        LiveRunCoordinator coordinator = LiveRunRuntime.coordinator(this);
        for (LiveRunState run : coordinator.registry().snapshot()) {
            if (!reconnectingSessions.add(run.sessionId())) continue;
            reconnectExecutor.execute(() -> reconnect(connections, connection, coordinator, run));
        }
    }

    private void reconnect(AnkaiConnectionStore connections, AnkaiConnection connection,
                           LiveRunCoordinator coordinator, LiveRunState run) {
        AnkaiClient client = connection.newClient();
        try {
            coordinator.reconnect(run.sessionId(), client::streamLiveRun);
            connections.saveSessionCookie(client.sessionCookie());
        } catch (IOException ignored) {
            // Persistierter Lauf bleibt fuer den naechsten Service-Start reconnectbar.
        } finally {
            reconnectingSessions.remove(run.sessionId());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Claude Assistant",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Claude Assistant is running");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Assistant is ready")
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .build();
    }

    @Override
    public void onDestroy() {
        reconnectExecutor.shutdownNow();
        super.onDestroy();
        stopForeground(true);
    }
}
