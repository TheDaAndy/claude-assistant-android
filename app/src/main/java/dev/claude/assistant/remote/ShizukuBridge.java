package dev.claude.assistant.remote;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;
import java.util.concurrent.*;
import org.json.JSONObject;

public final class ShizukuBridge {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Shizuku.UserServiceArgs args;
    private volatile CountDownLatch connected=new CountDownLatch(1);
    private volatile IPrivilegedDeviceService service;
    private volatile boolean closed;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;service=IPrivilegedDeviceService.Stub.asInterface(binder);connected.countDown();}
        @Override public void onServiceDisconnected(ComponentName name){service=null;}
    };
    public ShizukuBridge(Context context){args=new Shizuku.UserServiceArgs(new ComponentName(context,PrivilegedDeviceService.class))
        .tag("ankai-privileged-device").daemon(false).processNameSuffix("device_shell").version(dev.claude.assistant.BuildConfig.VERSION_CODE);}
    public static boolean running(){try{return Shizuku.pingBinder() && !Shizuku.isPreV11();}catch(Throwable unavailable){return false;}}
    public static boolean authorized(){try{return running() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Throwable unavailable){return false;}}
    public static int privilegeUid(){try{return authorized()?Shizuku.getUid():-1;}catch(Throwable unavailable){return -1;}}
    public JSONObject execute(String command,int timeout)throws Exception{
        if(closed||!authorized())throw new IllegalStateException("Shizuku nicht freigegeben oder gestoppt.");
        if(service==null){connected=new CountDownLatch(1);main.post(()->{try{if(!closed)Shizuku.bindUserService(args,connection);}catch(Exception ignored){connected.countDown();}});connected.await(10,TimeUnit.SECONDS);}
        IPrivilegedDeviceService s=service;
        if(s==null||closed||!authorized())throw new IllegalStateException("Shizuku-UserService nicht erreichbar.");
        final int expectedUid=privilegeUid();
        java.util.concurrent.atomic.AtomicBoolean revoked=new java.util.concurrent.atomic.AtomicBoolean(false);
        ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(()->{if(closed||!authorized()||privilegeUid()!=expectedUid){revoked.set(true);try{s.cancel();}catch(Exception ignored){}}},250,250,TimeUnit.MILLISECONDS);
        try {
            JSONObject result=new JSONObject(s.execute(command,timeout));
            if(revoked.get()||closed||!authorized())return new JSONObject().put("status","error").put("data","Rechte entzogen oder Kanal pausiert; Ausführung abgebrochen, Ergebnis unbekannt.");
            return result;
        }finally{monitor.shutdownNow();}
    }
    public void close(){closed=true;connected.countDown();IPrivilegedDeviceService s=service;if(s!=null)try{s.cancel();}catch(Exception ignored){}
        main.post(()->{try{Shizuku.unbindUserService(args,connection,true);}catch(Exception ignored){}});service=null;}
}
