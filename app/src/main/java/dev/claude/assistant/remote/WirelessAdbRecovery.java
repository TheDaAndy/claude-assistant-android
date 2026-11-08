package dev.claude.assistant.remote;

import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import dev.claude.assistant.ankai.AdbRecoveryPolicy;
import java.net.*;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded local mDNS reconnect attempts inside the existing visible device service. No pairing or wake lock. */
final class WirelessAdbRecovery implements AutoCloseable {
    private final Context context;
    private final DeviceLinkStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean queued=new AtomicBoolean();
    private volatile boolean closed;
    private volatile NsdManager.DiscoveryListener discovery;
    private volatile CountDownLatch scan;
    private volatile boolean connecting;
    private int failures;
    private volatile long nextAttempt;
    WirelessAdbRecovery(Context context,DeviceLinkStore store){this.context=context.getApplicationContext();this.store=store;}
    void start(){worker.scheduleWithFixedDelay(this::attempt,0,5,TimeUnit.SECONDS);}
    void networkAvailable(){nextAttempt=0;if(!closed&&queued.compareAndSet(false,true))try{worker.execute(()->{queued.set(false);attempt();});}catch(RejectedExecutionException ignored){queued.set(false);}}
    private boolean allowed(){return !closed&&AdbRecoveryPolicy.allowed(Build.VERSION.SDK_INT,store.enabled(),store.id()!=null,(store.maximumGranted(2000)||store.maximumGranted(0)),
        store.wirelessIdentityAvailable(),WirelessPairingService.active());}
    private void attempt(){
        try{
            if(!allowed()||WirelessAdbBridge.running()||SystemClock.elapsedRealtime()<nextAttempt)return;
            int port=discoverPort();
            if(port<=0||!allowed()||WirelessAdbBridge.running()){retry();return;}
            WirelessAdbConnection connection=WirelessAdbConnection.get(context);connecting=true;
            try{
                if(!allowed())return;
                connection.disconnect();
                if(!connection.connect("127.0.0.1",port))throw new java.io.IOException();
                connection.verifyShell();
                if(!allowed()||!store.maximumGranted(connection.uid())){connection.disconnect();return;}
                failures=0;nextAttempt=0;DevicePresenceMonitor.changed();
            }catch(Exception unavailable){try{connection.disconnect();}catch(Exception ignored){}retry();}
            finally{connecting=false;}
        }catch(Exception unavailable){retry();}
    }
    private void retry(){nextAttempt=SystemClock.elapsedRealtime()+AdbRecoveryPolicy.retryMillis(++failures);}
    private int discoverPort()throws InterruptedException {
        NsdManager nsd=context.getSystemService(NsdManager.class);CountDownLatch latch=new CountDownLatch(1);scan=latch;
        AtomicInteger port=new AtomicInteger();
        class ScanListener implements NsdManager.DiscoveryListener {
            private final ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();
            private boolean resolving;
            @Override public void onDiscoveryStarted(String t){}
            @Override public void onDiscoveryStopped(String t){}
            @Override public void onStartDiscoveryFailed(String t,int e){latch.countDown();}
            @Override public void onStopDiscoveryFailed(String t,int e){}
            @Override public void onServiceLost(NsdServiceInfo info){}
            @Override public void onServiceFound(NsdServiceInfo info){main.post(()->{
                if(closed||discovery!=this||queue.size()>=32)return;queue.add(info);resolveNext();
            });}
            private void resolveNext(){
                if(closed||discovery!=this||resolving||queue.isEmpty())return;
                resolving=true;NsdServiceInfo info=queue.remove();
                try{nsd.resolveService(info,new NsdManager.ResolveListener(){
                    @Override public void onResolveFailed(NsdServiceInfo s,int e){main.post(()->{resolving=false;resolveNext();});}
                    @Override public void onServiceResolved(NsdServiceInfo s){main.post(()->{
                        resolving=false;if(closed||discovery!=ScanListener.this)return;
                        if(local(s.getHost())&&s.getPort()>0&&s.getPort()<=65535){port.compareAndSet(0,s.getPort());latch.countDown();}
                        else resolveNext();
                    });}
                });}catch(RuntimeException unavailable){resolving=false;resolveNext();}
            }
        }
        NsdManager.DiscoveryListener listener=new ScanListener();
        discovery=listener;
        main.post(()->{if(closed||discovery!=listener){latch.countDown();return;}try{nsd.discoverServices("_adb-tls-connect._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);}catch(RuntimeException unavailable){latch.countDown();}});
        try{latch.await(12,TimeUnit.SECONDS);return port.get();}
        finally{scan=null;if(discovery==listener)discovery=null;main.post(()->{try{nsd.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}});}
    }
    private static boolean local(InetAddress host){
        if(host==null||host.isAnyLocalAddress())return false;if(host.isLoopbackAddress())return true;
        try{return NetworkInterface.getByInetAddress(host)!=null;}catch(SocketException|SecurityException denied){return false;}
    }
    @Override public void close(){
        closed=true;CountDownLatch latch=scan;if(latch!=null)latch.countDown();worker.shutdownNow();
        if(connecting)new Thread(()->{WirelessAdbConnection c=WirelessAdbConnection.current();if(c!=null)try{c.disconnect();}catch(Exception ignored){}},"adb-recovery-stop").start();
    }
}
