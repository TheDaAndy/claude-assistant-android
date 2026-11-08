package dev.claude.assistant.qa;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import dev.claude.assistant.remote.*;
import java.lang.reflect.*;
import org.json.JSONObject;
public final class WirelessInstrumentation extends Instrumentation {
 private Bundle args;
 @Override public void onCreate(Bundle b){args=b;start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();
  int expectedUid=Integer.parseInt(args.getString("expectedUid","2000"));
  if("auto-debug".equals(args.getString("mode"))){
   DeviceLinkStore store=new DeviceLinkStore(c);store.save("qa-auto-debug","qa-only","http://10.0.2.2:9","QA only");store.maximum(true,expectedUid);store.enabled(true);store.autoDebugEnabled(true);store.autoDebugBoot(-1);
   Class<?> cls=Class.forName("dev.claude.assistant.remote.WirelessDebuggingAutostart");Method enable=cls.getDeclaredMethod("enableIfNeeded",Context.class,DeviceLinkStore.class);enable.setAccessible(true);
   boolean permission=c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)==android.content.pm.PackageManager.PERMISSION_GRANTED;
   if(!permission){if(Boolean.TRUE.equals(enable.invoke(null,c,store)))throw new Exception("Enabled without permission");result.putString("result","PASS: missing secure-settings permission denies activation");}
   else{
    android.provider.Settings.Global.putInt(c.getContentResolver(),"adb_wifi_enabled",0);
    if(!Boolean.TRUE.equals(enable.invoke(null,c,store)))throw new Exception("Auto-enable denied with permission and consent");
    if(android.provider.Settings.Global.getInt(c.getContentResolver(),"adb_wifi_enabled",0)!=1)throw new Exception("Setting not enabled");
    android.provider.Settings.Global.putInt(c.getContentResolver(),"adb_wifi_enabled",0);
    if(Boolean.TRUE.equals(enable.invoke(null,c,store)))throw new Exception("Overrode manual disable within same boot");
    store.autoDebugEnabled(false);store.autoDebugBoot(-1);if(Boolean.TRUE.equals(enable.invoke(null,c,store)))throw new Exception("Opt-out ignored");
    store.autoDebugEnabled(true);store.enabled(false);if(Boolean.TRUE.equals(enable.invoke(null,c,store)))throw new Exception("Pause ignored");
    result.putString("result","PASS: real secure-settings activation, once-per-boot, opt-out and pause");
   }
   store.enabled(false);finish(-1,result);return;
  }
  if("recovery-seed".equals(args.getString("mode"))){
   DeviceLinkStore store=new DeviceLinkStore(c);store.save("qa-recovery","qa-only","http://10.0.2.2:9","QA only");store.maximum(true,expectedUid);store.enabled(true);Thread.sleep(1000);
   result.putString("result","PASS: paired identity and local consent persist for next process");finish(-1,result);return;
  }
  if("recovery".equals(args.getString("mode"))){
   if(WirelessAdbBridge.running())throw new Exception("Expected a fresh process without ADB transport");
   Class<?> cls=Class.forName("dev.claude.assistant.remote.WirelessAdbRecovery");
   java.lang.reflect.Constructor<?> ctor=cls.getDeclaredConstructor(Context.class,DeviceLinkStore.class);ctor.setAccessible(true);
   Method start=cls.getDeclaredMethod("start"),close=cls.getDeclaredMethod("close"),attempt=cls.getDeclaredMethod("attempt");start.setAccessible(true);close.setAccessible(true);attempt.setAccessible(true);
   DeviceLinkStore store=new DeviceLinkStore(c);if(!store.enabled()||!store.maximumGranted(expectedUid))throw new Exception("Missing persisted grant enabled="+store.enabled()+" maximum="+store.maximumGranted(expectedUid)+" linked="+(store.id()!=null));
   Object recovery=ctor.newInstance(c,store);start.invoke(recovery);
   try{
    long until=android.os.SystemClock.elapsedRealtime()+45000;while(!WirelessAdbBridge.authorized()&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(200);
    if(!WirelessAdbBridge.authorized())throw new Exception("Fresh-process automatic reconnect failed");
    JSONObject data=new WirelessAdbBridge(c).execute("printf recovered",3).getJSONObject("data");if(!"recovered".equals(data.getString("stdout")))throw new Exception("Recovery shell failed");
   }finally{close.invoke(recovery);}
   Class<?> connection=Class.forName("dev.claude.assistant.remote.WirelessAdbConnection");Method get=connection.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object manager=get.invoke(null,c);connection.getMethod("disconnect").invoke(manager);
   store.enabled(false);Object paused=ctor.newInstance(c,store);attempt.invoke(paused);close.invoke(paused);if(WirelessAdbBridge.running())throw new Exception("Paused channel reconnected");
   store.enabled(true);store.maximum(false,-1);Object revoked=ctor.newInstance(c,store);attempt.invoke(revoked);close.invoke(revoked);if(WirelessAdbBridge.running())throw new Exception("Revoked grant reconnected");store.enabled(false);
   result.putString("result","PASS: fresh-process persisted identity automatic local TLS reconnect, UID2000 + shell, pause and revoked consent prevent reconnect");finish(-1,result);return;
  }
  if("notification-cancel".equals(args.getString("mode"))){
   c.startForegroundService(new Intent(c,WirelessPairingService.class).setAction(WirelessPairingService.START));
   Notification notice=null;long limit=android.os.SystemClock.elapsedRealtime()+5000;
   while(notice==null&&android.os.SystemClock.elapsedRealtime()<limit){
    for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==WirelessPairingService.NOTIFICATION_ID&&n.getNotification().actions!=null)notice=n.getNotification();
    Thread.sleep(100);
   }
   if(notice==null)throw new Exception("No pairing notification");
   Notification.Action reply=null,cancel=null;for(Notification.Action action:notice.actions){if(action.getRemoteInputs()!=null)reply=action;else cancel=action;}
   if(reply==null||cancel==null)throw new Exception("Missing notification actions");
   Intent fill=new Intent();Bundle invalid=new Bundle();invalid.putCharSequence(WirelessPairingService.INPUT,"123");RemoteInput.addResultsToIntent(reply.getRemoteInputs(),fill,invalid);reply.actionIntent.send(c,0,fill);
   Thread.sleep(300);if(!WirelessPairingService.status().contains("sechsstelligen"))throw new Exception("Invalid reply not rejected");
   cancel.actionIntent.send();Thread.sleep(500);if(!WirelessPairingService.status().contains("abgebrochen"))throw new Exception("Cancel failed");
   reply.actionIntent.send(c,0,fill);Thread.sleep(500);if(!WirelessPairingService.status().contains("abgebrochen"))throw new Exception("Stale reply resurrected session");
   result.putString("result","PASS: invalid reply, cancel, stale PendingIntent rejection");finish(-1,result);return;
  }
  if("notification".equals(args.getString("mode"))){
   c.startForegroundService(new Intent(c,WirelessPairingService.class).setAction(WirelessPairingService.START));
   final android.content.BroadcastReceiver receiver=new android.content.BroadcastReceiver(){
    @Override public void onReceive(Context context,Intent intent){try{
      android.service.notification.StatusBarNotification[] notices=c.getSystemService(NotificationManager.class).getActiveNotifications();
      for(android.service.notification.StatusBarNotification notice:notices){
       if(notice.getId()!=WirelessPairingService.NOTIFICATION_ID)continue;
       Notification.Action[] actions=notice.getNotification().actions;if(actions==null)continue;
       for(Notification.Action action:actions){if(action.getRemoteInputs()==null)continue;
        Intent fill=new Intent();Bundle values=new Bundle();values.putCharSequence(WirelessPairingService.INPUT,intent.getStringExtra("code"));
        RemoteInput.addResultsToIntent(action.getRemoteInputs(),fill,values);action.actionIntent.send(c,0,fill);return;
       }
      }
    }catch(Exception ignored){}}
   };
   if(android.os.Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,new IntentFilter("ankai.qa.PAIR_REPLY"),Context.RECEIVER_EXPORTED);
   else c.registerReceiver(receiver,new IntentFilter("ankai.qa.PAIR_REPLY"));
   try{
    long deadline=android.os.SystemClock.elapsedRealtime()+180000;
    while(!WirelessAdbBridge.authorized()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(250);
    if(!WirelessAdbBridge.authorized())throw new Exception("Notification pairing failed: "+WirelessPairingService.status());
    JSONObject data=new WirelessAdbBridge(c).execute("printf notification-ok",3).getJSONObject("data");
    if(!"notification-ok".equals(data.getString("stdout")))throw new Exception("notification shell failed");
    result.putString("result","PASS: notification RemoteInput, local mDNS pairing + connect discovery, actual TLS pairing, UID2000 and shell");
    finish(-1,result);return;
   }finally{c.unregisterReceiver(receiver);}
  }
  if("ui".equals(args.getString("mode"))){
   if(args.containsKey("port")){
    Class<?> connection=Class.forName("dev.claude.assistant.remote.WirelessAdbConnection");Method get=connection.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object manager=get.invoke(null,c);
    connection.getMethod("connect",String.class,int.class).invoke(manager,"127.0.0.1",Integer.parseInt(args.getString("port")));Method verify=connection.getDeclaredMethod("verifyShell");verify.setAccessible(true);verify.invoke(manager);
   }
   Activity a=startActivitySync(new Intent(c,MaximumControlActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(60000);result.putString("screen",a.getClass().getSimpleName());finish(-1,result);return;
  }
  Class<?> cls=Class.forName("dev.claude.assistant.remote.WirelessAdbConnection");
  Method get=cls.getDeclaredMethod("get",Context.class);get.setAccessible(true);Object manager=get.invoke(null,c);
  if(args.containsKey("pairPort")){
   Object paired=cls.getMethod("pair",String.class,int.class,String.class).invoke(manager,"127.0.0.1",Integer.parseInt(args.getString("pairPort")),args.getString("code"));
   if(!Boolean.TRUE.equals(paired))throw new Exception("pair failed");
  }
  Object connected=cls.getMethod("connect",String.class,int.class).invoke(manager,"127.0.0.1",Integer.parseInt(args.getString("port")));
  if(!Boolean.TRUE.equals(connected))throw new Exception("connect failed");
  Method verify=cls.getDeclaredMethod("verifyShell");verify.setAccessible(true);verify.invoke(manager);
  WirelessAdbBridge bridge=new WirelessAdbBridge(c);
  JSONObject response=bridge.execute("printf hello; printf problem >&2; exit 7",5),data=response.getJSONObject("data");
  if(data.getInt("uid")!=expectedUid||data.getInt("exitCode")!=7||!"hello".equals(data.getString("stdout"))||!"problem".equals(data.getString("stderr")))throw new Exception("shell output mismatch: "+response);
  JSONObject timed=bridge.execute("sleep 10",1).getJSONObject("data");if(!timed.getBoolean("timedOut"))throw new Exception("timeout not enforced");
  JSONObject cap=bridge.execute("yes x | head -c 10000",5).getJSONObject("data");if(!cap.getBoolean("truncated")||cap.getString("stdout").length()!=2048)throw new Exception("output not capped");
  bridge.close();try{bridge.execute("echo unexpected",1);throw new Exception("closed bridge executed");}catch(java.io.IOException expected){}
  cls.getMethod("disconnect").invoke(manager);if(WirelessAdbBridge.authorized())throw new Exception("disconnect kept capability");
  result.putString("result","PASS: local ADB, verified UID, stdout/stderr/exit, timeout, cap, close, disconnect");finish(-1,result);
 }catch(Throwable t){result.putString("failure",t.toString()+" cause="+t.getCause());finish(1,result);}}
}
