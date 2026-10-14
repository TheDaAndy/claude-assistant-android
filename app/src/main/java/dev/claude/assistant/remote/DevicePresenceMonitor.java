package dev.claude.assistant.remote;

import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import dev.claude.assistant.ankai.RecentInteraction;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Event driven state reports + 30s heartbeat. No content collection or wake lock. */
final class DevicePresenceMonitor implements AutoCloseable {
    private static volatile DevicePresenceMonitor current;
    private final Context context;
    private final DeviceLinkStore store;
    interface DeviceUpdate { void apply(JSONObject device)throws Exception; }
    private final DeviceUpdate update;
    private final DeviceHttp http=new DeviceHttp();
    private final RecentInteraction interaction=new RecentInteraction();
    private final ScheduledExecutorService sender=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean queued=new AtomicBoolean();
    private volatile boolean closed;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){changed();}
    };
    DevicePresenceMonitor(Context context,DeviceLinkStore store,DeviceUpdate update){this.context=context;this.store=store;this.update=update;}
    void start(){
        current=this;
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,f);
        sender.scheduleWithFixedDelay(this::report,0,30,TimeUnit.SECONDS);
    }
    static void changed(){DevicePresenceMonitor m=current;if(m!=null)m.queue();}
    static void onEvent(int type){
        DevicePresenceMonitor m=current;
        if(m==null || !m.monitoring())return;
        if(type==AccessibilityEvent.TYPE_VIEW_CLICKED || type==AccessibilityEvent.TYPE_VIEW_SCROLLED
                || type==AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || type==AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
                || type==AccessibilityEvent.TYPE_TOUCH_INTERACTION_END){
            m.interaction.record(SystemClock.elapsedRealtime());m.queue();
        }
    }
    private boolean monitoring(){return store.enabled()&&store.uiGranted()&&UiControlService.ready();}
    private void queue(){
        if(closed || !queued.compareAndSet(false,true))return;
        try{sender.schedule(()->{queued.set(false);report();},1,TimeUnit.SECONDS);}catch(RejectedExecutionException stopped){queued.set(false);}
    }
    JSONObject snapshot()throws Exception{
        KeyguardManager lock=context.getSystemService(KeyguardManager.class);
        boolean monitoring=monitoring();Long age=interaction.age(SystemClock.elapsedRealtime(),monitoring);
        return new JSONObject().put("sequence",store.nextControlSequence())
                .put("interactive",context.getSystemService(PowerManager.class).isInteractive())
                .put("keyguardLocked",lock.isKeyguardLocked()).put("deviceLocked",lock.isDeviceLocked())
                .put("activityMonitoring",monitoring).put("lastInteractionAgeMs",age==null?JSONObject.NULL:age);
    }
    private void report(){
        if(closed || !store.enabled() || store.id()==null)return;
        try{
            int uid=ShizukuBridge.privilegeUid();
            JSONObject capabilities=new JSONObject().put("sequence",store.nextControlSequence()).put("enabled",store.enabled()&&store.maximumGranted(uid)).put("ready",ShizukuBridge.authorized())
                .put("uid",uid==0||uid==2000?uid:JSONObject.NULL).put("uiEnabled",store.enabled()&&store.uiGranted()).put("uiReady",UiControlService.ready());
            JSONObject response=http.channel(store,"/capabilities","POST",capabilities);
            JSONObject device=response.optJSONObject("device");if(device!=null)update.apply(device);
            if(!closed&&store.enabled())http.channel(store,"/presence","POST",snapshot());
        }catch(Exception unavailable){/* Normal command channel owns reconnect/revocation; stale presence expires server-side. */}
    }
    @Override public void close(){
        closed=true;if(current==this)current=null;
        try{context.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}
        http.cancel();sender.shutdownNow();
    }
}
