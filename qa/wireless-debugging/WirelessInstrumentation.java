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
  if(data.getInt("uid")!=2000||data.getInt("exitCode")!=7||!"hello".equals(data.getString("stdout"))||!"problem".equals(data.getString("stderr")))throw new Exception("shell output mismatch: "+response);
  JSONObject timed=bridge.execute("sleep 10",1).getJSONObject("data");if(!timed.getBoolean("timedOut"))throw new Exception("timeout not enforced");
  JSONObject cap=bridge.execute("yes x | head -c 10000",5).getJSONObject("data");if(!cap.getBoolean("truncated")||cap.getString("stdout").length()!=2048)throw new Exception("output not capped");
  bridge.close();try{bridge.execute("echo unexpected",1);throw new Exception("closed bridge executed");}catch(java.io.IOException expected){}
  cls.getMethod("disconnect").invoke(manager);if(WirelessAdbBridge.authorized())throw new Exception("disconnect kept capability");
  result.putString("result","PASS: local ADB, verified UID, stdout/stderr/exit, timeout, cap, close, disconnect");finish(-1,result);
 }catch(Throwable t){result.putString("failure",t.toString()+" cause="+t.getCause());finish(1,result);}}
}
