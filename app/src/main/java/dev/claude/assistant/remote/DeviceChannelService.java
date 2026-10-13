package dev.claude.assistant.remote;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.content.pm.ServiceInfo;
import dev.claude.assistant.MainActivity;
import dev.claude.assistant.R;
import dev.claude.assistant.ankai.DeviceCommandPolicy;
import org.json.JSONObject;
import java.util.concurrent.*;

/** User-started, visible and stoppable; no boot restart and no wakelock. */
public final class DeviceChannelService extends Service {
    public static final String STOP = "device.stop";
    public static final String CHANNEL = "device_remote";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DeviceHttp http = new DeviceHttp();
    private volatile boolean running;
    private DeviceLinkStore store;
    private ShizukuBridge bridge;
    private final java.util.Set<Integer> actionNotifications = new java.util.HashSet<>();
    public static int notificationId(String id) { return 10000 + (id.hashCode() & 0x3fffffff); }

    @Override public void onCreate() {
        super.onCreate(); store = new DeviceLinkStore(this); bridge=new ShizukuBridge(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Ankaï-Gerätezugriff", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { store.enabled(false); stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        if (store.id() == null || !store.enabled()) { stopSelf(); return START_NOT_STICKY; }
        Notification n = foregroundNotification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(2801, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(2801, n);
        running = true; worker.execute(this::loop); return START_NOT_STICKY;
    }
    private Notification foregroundNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, DeviceChannelService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_assistant)
                .setContentTitle("Ankaï-Gerätezugriff aktiv").setContentText("Freigabe: " + store.scopeLabel())
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "Pausieren", stop).build()).build();
    }
    private void loop() {
        int failures = 0;
        while (running && store.enabled()) {
            try {
                int uid=ShizukuBridge.privilegeUid();
                JSONObject capabilities=new JSONObject().put("sequence",store.nextControlSequence()).put("enabled",store.enabled() && store.maximumGranted(uid)).put("ready",ShizukuBridge.authorized())
                        .put("uid",uid==0||uid==2000?uid:JSONObject.NULL);
                JSONObject capabilityResponse=http.channel(store,"/capabilities","POST",capabilities);
                JSONObject capabilityDevice=capabilityResponse.optJSONObject("device");
                if(capabilityDevice!=null)applyDevice(capabilityDevice);
                store.status("Verbunden · wartet auf Aktionen");
                JSONObject response = http.channel(store, "/commands", "GET", null);
                JSONObject device = response.optJSONObject("device");
                if(device!=null)applyDevice(device);
                JSONObject command = response.optJSONObject("command");
                failures = 0;
                if (!running || !store.enabled()) break;
                if (command == null) continue;
                String id = command.getString("id");
                JSONObject result = store.cachedResult(id);
                if (result == null) {
                    result = execute(command);
                    // Save before upload; retries do not repeat a user-facing action.
                    if (!running || !store.enabled()) break;
                    store.cacheResult(id, result);
                }
                if (!running || !store.enabled()) break;
                http.channel(store, "/commands/" + id + "/result", "POST", result);
                store.status("Letzte Aktion: " + command.optString("action"));
            } catch (DeviceHttp.Failure error) {
                if (error.status == 401 || error.status == 404) {
                    store.enabled(false); store.status("Kopplung widerrufen · neu koppeln"); break;
                }
                if (error.status == 409) continue; // An expired leased command is never executed again.
                if (!backoff(++failures)) break;
            } catch (Exception error) { if (!backoff(++failures)) break; }
        }
        stopSelf();
    }
    private void applyDevice(JSONObject device) throws Exception {
        if(device.optLong("scopeRevision",0)==store.scopeRevision())return;
        synchronized(actionNotifications) {
            for(int notificationId:actionNotifications)getSystemService(NotificationManager.class).cancel(notificationId);
            actionNotifications.clear();
        }
        store.updateDevice(device);
        if(running)getSystemService(NotificationManager.class).notify(2801,foregroundNotification());
    }
    private boolean backoff(int failures) {
        store.status("Offline · Wiederverbindung mit Wartezeit");
        try { TimeUnit.SECONDS.sleep(DeviceCommandPolicy.retrySeconds(failures)); return running && store.enabled(); }
        catch (InterruptedException stop) { Thread.currentThread().interrupt(); return false; }
    }
    private JSONObject execute(JSONObject c) throws Exception {
        String action = c.optString("action"); String target = c.optString("target");
        if (!DeviceCommandPolicy.matchesGrant(c.optLong("scopeRevision",0),store.scopeRevision())
                || !DeviceCommandPolicy.allowed(action, target, c.optLong("expiresAt"), System.currentTimeMillis()))
            return new JSONObject().put("status", "error").put("data", "Unzulässige oder abgelaufene Aktion");
        JSONObject data = new JSONObject();
        if("remote_shell".equals(action)) {
            int uid=ShizukuBridge.privilegeUid();
            if(!store.maximumGranted(uid)||!ShizukuBridge.authorized())return new JSONObject().put("status","error").put("data","Maximalfreigabe/Shizuku auf dem Handy nicht aktiv.");
            String command=c.getString("command");int timeout=c.optInt("timeoutSeconds",15);
            dev.claude.assistant.ankai.PrivilegedCommandPolicy.validate(command,timeout);
            // Durable uncertainty marker is written before any privileged side effect.
            store.cacheResult(c.getString("id"),new JSONObject().put("status","error").put("data","Befehlsausführung unterbrochen; Ergebnis unbekannt. Nicht automatisch wiederholen."));
            if(!running||!store.enabled())return new JSONObject().put("status","error").put("data","Kanal pausiert.");
            try {return bridge.execute(command,timeout);}
            catch(Exception unavailable){return new JSONObject().put("status","error").put("data","Shizuku-Ausführung fehlgeschlagen; Ergebnis unbekannt. Nicht automatisch wiederholen.");}
        }
        if ("device_info".equals(action)) {
            data.put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                    .put("androidVersion", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                    .put("securityPatch", Build.VERSION.SECURITY_PATCH)
                    .put("appVersion", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            BatteryManager battery = getSystemService(BatteryManager.class);
            data.put("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        } else if ("settings_snapshot".equals(action)) {
            PowerManager power = getSystemService(PowerManager.class);
            data.put("batterySaver", power.isPowerSaveMode()).put("ignoringBatteryOptimizations", power.isIgnoringBatteryOptimizations(getPackageName()))
                    .put("overlayAllowed", Settings.canDrawOverlays(this))
                    .put("notificationsEnabled", getSystemService(NotificationManager.class).areNotificationsEnabled())
                    .put("airplaneMode", Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
                    .put("automaticTime", Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1)
                    .put("adbAvailable",ShizukuBridge.authorized()).put("shizukuRunning",ShizukuBridge.running())
                    .put("maximumRequested",store.maximumRequested()).put("remoteShellEnabled",store.maximumGranted(ShizukuBridge.privilegeUid()) && ShizukuBridge.authorized()).put("scope", "Nur freigegebene, öffentlich lesbare Einstellungen; keine globalen Logs oder privaten App-Daten.");
        } else {
            if (!getSystemService(NotificationManager.class).areNotificationsEnabled())
                return new JSONObject().put("status", "error").put("data", "Benachrichtigungen deaktiviert");
            Intent intent = new Intent(this, DeviceActionActivity.class).setData(Uri.parse("ankai-device:" + c.getString("id")))
                    .putExtra("target", target).putExtra("expiresAt", c.getLong("expiresAt"))
                    .putExtra("scopeRevision",c.optLong("scopeRevision",0)).putExtra("requester",c.optString("requester"))
                    .putExtra("chatTitle",c.optString("chatTitle",""));
            int nid = notificationId(c.getString("id"));
            PendingIntent confirm = PendingIntent.getActivity(this, nid, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_assistant)
                    .setContentTitle("Ankaï möchte Einstellungen öffnen").setContentText(c.optString("requester") + " · " + target + " · Antippen zum Prüfen")
                    .setContentIntent(confirm).setAutoCancel(true).setTimeoutAfter(Math.max(1, c.getLong("expiresAt") - System.currentTimeMillis())).build();
            synchronized (actionNotifications) { actionNotifications.add(nid); }
            getSystemService(NotificationManager.class).notify(nid, n);
            return new JSONObject().put("status", "confirmation_required").put("data", "Am Handy bestätigen; noch keine Einstellung geöffnet oder geändert.");
        }
        return new JSONObject().put("status", "ok").put("data", data);
    }
    @Override public void onDestroy() {
        running = false; http.cancel(); if(bridge!=null)bridge.close(); worker.shutdownNow();
        if(store!=null && store.id()!=null) {
            final long disabledSequence=store.nextControlSequence();
            new Thread(()->{try{new DeviceHttp().channel(store,"/capabilities","POST",new JSONObject().put("sequence",disabledSequence).put("enabled",false).put("ready",false).put("uid",JSONObject.NULL));}catch(Exception ignored){}},"device-disable-report").start();
        }
        synchronized (actionNotifications) { for (int id : actionNotifications) getSystemService(NotificationManager.class).cancel(id); }
        if (store != null) { store.enabled(false); if (!store.status().startsWith("Kopplung widerrufen")) store.status("Pausiert · in der App wieder aktivieren"); }
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
