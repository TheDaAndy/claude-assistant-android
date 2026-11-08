package dev.claude.assistant.remote;

import android.app.*;
import android.content.*;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import dev.claude.assistant.ankai.PairingReply;

/** User-started, five-minute foreground pairing session. Never persists or logs a pairing code. */
public final class WirelessPairingService extends Service {
    public static final String START="wireless.pair.start",REPLY="wireless.pair.reply",CANCEL="wireless.pair.cancel",INPUT="pairing_code";
    public static final int NOTIFICATION_ID=7304;
    private static final String CHANNEL="wireless_pairing";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<NsdManager.DiscoveryListener> discoveries=new ArrayList<>();
    private final ArrayDeque<NsdServiceInfo> resolveQueue=new ArrayDeque<>();
    private NsdManager nsd;
    private String session,pendingCode;
    private int pairingPort,connectPort;
    private boolean resolving,busy,paired,closed,connecting;
    private static volatile boolean active;
    public static boolean active(){return active;}
    private static volatile String lastStatus="";
    public static String status(){return lastStatus;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();nsd=getSystemService(NsdManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"WLAN-Debugging koppeln",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Code direkt eingeben, während die Android-Einstellungen geöffnet bleiben");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if(START.equals(action)){
            if(session!=null)return START_NOT_STICKY;
            active=true;session=UUID.randomUUID().toString();
            show("In den Einstellungen WLAN-Debugging und ›Mit Kopplungscode koppeln‹ öffnen.",true);
            discover("_adb-tls-pairing._tcp.");discover("_adb-tls-connect._tcp.");
            main.postDelayed(()->finish("Kopplung abgelaufen. In Ankaï erneut starten."),300000);
        }else{
            if(session==null||!session.equals(intent.getStringExtra("session"))){if(session==null)stopSelf();return START_NOT_STICKY;}
            if(CANCEL.equals(action)){finish("Kopplung abgebrochen.");return START_NOT_STICKY;}
            if(REPLY.equals(action)&&!busy&&!paired){
                Bundle results=RemoteInput.getResultsFromIntent(intent);
                try{
                    PairingReply reply=PairingReply.parse(results==null?null:results.getCharSequence(INPUT));
                    if(reply.port!=0)pairingPort=reply.port;
                    pendingCode=reply.code;
                    main.postDelayed(()->{if(pendingCode!=null){pendingCode=null;show("Kein lokaler Kopplungsport erkannt. Alternativ ›Port Code‹ eingeben, z. B. den angezeigten Port und danach den Code.",true);}},30000);
                    tryPair();
                }catch(IllegalArgumentException invalid){show("Bitte sechsstelligen Code eingeben. Alternativ: Port Leerzeichen Code.",true);}
                intent.replaceExtras((Bundle)null);intent.setClipData(null);
            }
        }
        return START_NOT_STICKY;
    }
    private PendingIntent action(String action,boolean mutable){
        Intent intent=new Intent(this,WirelessPairingService.class).setAction(action).setData(android.net.Uri.parse("ankai-pair:"+session)).putExtra("session",session);
        return PendingIntent.getService(this,action.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|(mutable?(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0):PendingIntent.FLAG_IMMUTABLE));
    }
    private Notification notification(String message,boolean input,boolean ongoing){
        PendingIntent open=PendingIntent.getActivity(this,7304,new Intent(this,MaximumControlActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(dev.claude.assistant.R.drawable.ic_assistant)
            .setContentTitle("Ankaï · WLAN-Kopplung").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing);
        if(input){RemoteInput remote=new RemoteInput.Builder(INPUT).setLabel("Sechsstelliger Kopplungscode").build();
            Notification.Action.Builder reply=new Notification.Action.Builder(null,"Code eingeben",action(REPLY,true)).addRemoteInput(remote).setAllowGeneratedReplies(false);
            if(Build.VERSION.SDK_INT>=31)reply.setAuthenticationRequired(true);
            builder.addAction(reply.build());}
        if(ongoing)builder.addAction(new Notification.Action.Builder(null,"Abbrechen",action(CANCEL,false)).build());else builder.setTimeoutAfter(60000);
        return builder.build();
    }
    private void show(String message,boolean input){if(closed)return;lastStatus=message;startForeground(NOTIFICATION_ID,notification(message,input,true));}
    private void finish(String message){
        if(closed)return;closed=true;lastStatus=message;pendingCode=null;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(message,false,false));
        stopForeground(STOP_FOREGROUND_DETACH);stopSelf();
    }
    private void discover(String type){
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            @Override public void onDiscoveryStarted(String t){}
            @Override public void onDiscoveryStopped(String t){}
            @Override public void onStartDiscoveryFailed(String t,int e){show("Portsuche nicht verfügbar. In der Benachrichtigung ›Port Code‹ eingeben.",true);}
            @Override public void onStopDiscoveryFailed(String t,int e){}
            @Override public void onServiceFound(NsdServiceInfo info){main.post(()->{if(!closed&&resolveQueue.size()<32){resolveQueue.add(info);resolveNext();}});}
            @Override public void onServiceLost(NsdServiceInfo info){}
        };
        discoveries.add(listener);
        try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);}catch(RuntimeException unavailable){show("Portsuche nicht verfügbar. In der Benachrichtigung ›Port Code‹ eingeben.",true);}
    }
    private void resolveNext(){
        if(closed||resolving||resolveQueue.isEmpty())return;
        resolving=true;NsdServiceInfo info=resolveQueue.remove();
        nsd.resolveService(info,new NsdManager.ResolveListener(){
            @Override public void onResolveFailed(NsdServiceInfo service,int error){main.post(()->{resolving=false;resolveNext();});}
            @Override public void onServiceResolved(NsdServiceInfo service){main.post(()->{
                resolving=false;if(closed)return;
                if(local(service.getHost())&&service.getPort()>0&&service.getPort()<=65535){
                    if(service.getServiceType().contains("_adb-tls-pairing")){
                        pairingPort=service.getPort();if(!busy&&!paired)show("Kopplungsport erkannt. ›Code eingeben‹ antippen und den sechsstelligen Code senden.",true);tryPair();
                    }else if(service.getServiceType().contains("_adb-tls-connect")){connectPort=service.getPort();tryConnect();}
                }
                resolveNext();
            });}
        });
    }
    private static boolean local(InetAddress host){
        if(host==null||host.isAnyLocalAddress())return false;
        if(host.isLoopbackAddress())return true;
        try{return NetworkInterface.getByInetAddress(host)!=null;}catch(SocketException|SecurityException denied){return false;}
    }
    private void tryPair(){
        if(closed||busy||paired||pendingCode==null)return;
        if(pairingPort==0){show("Code empfangen · suche lokalen Kopplungsport …",false);return;}
        String pin=pendingCode;pendingCode=null;int port=pairingPort;busy=true;show("Kopplung läuft … Android-Dialog geöffnet lassen.",false);
        worker.submit(()->{boolean ok=false;try{ok=WirelessAdbConnection.get(this).pair("127.0.0.1",port,pin);}catch(Exception ignored){}final boolean success=ok;
            main.post(()->{if(closed)return;busy=false;paired=success;
                if(success){show("Gekoppelt · verbinde automatisch mit diesem Handy …",false);tryConnect();}
                else{pairingPort=0;show("Kopplung fehlgeschlagen. Android-Dialog neu öffnen und aktuellen Code senden; alternativ ›Port Code‹.",true);}
            });});
    }
    private void tryConnect(){
        if(closed||busy||!paired||connectPort==0)return;
        busy=true;connecting=true;int port=connectPort;
        worker.submit(()->{boolean ok=false;String error="";try{
            WirelessAdbConnection c=WirelessAdbConnection.get(this);if(closed)return;
            c.disconnect();if(!c.connect("127.0.0.1",port))throw new Exception();c.verifyShell();ok=true;
        }catch(Exception failure){error=WirelessAdbConnection.failureMessage(failure);}final boolean success=ok;final String detail=error;
            main.post(()->{connecting=false;if(closed){try{WirelessAdbConnection.get(this).disconnect();}catch(Exception ignored){}return;}
                busy=false;DevicePresenceMonitor.changed();
                finish(success?"Verbunden · zurück zu Ankaï und ADB-Zugriff freigeben.":"Gekoppelt. "+detail);
            });});
    }
    @Override public void onDestroy(){active=false;closed=true;pendingCode=null;main.removeCallbacksAndMessages(null);
        for(NsdManager.DiscoveryListener listener:discoveries)try{nsd.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}
        resolveQueue.clear();worker.shutdownNow();
        if(connecting){WirelessAdbConnection c=WirelessAdbConnection.current();if(c!=null)try{c.disconnect();}catch(Exception ignored){}}
        super.onDestroy();
    }
}
