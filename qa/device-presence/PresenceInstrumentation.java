package dev.claude.assistant.qa;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import dev.claude.assistant.remote.DeviceLinkStore;
import dev.claude.assistant.remote.DeviceChannelService;
import org.json.JSONObject;
/** Separate test APK only. Seeds a localhost test pairing; never enters the shipped app. */
public final class PresenceInstrumentation extends Instrumentation {
 private Bundle args;
 @Override public void onCreate(Bundle args){this.args=args;start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try {
   Context c=getTargetContext();
   startActivitySync(new Intent(c,dev.claude.assistant.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   DeviceLinkStore store=new DeviceLinkStore(c);
   store.save(args.getString("deviceId"),args.getString("token"),args.getString("baseUrl"),"QA only");
   store.updateDevice(new JSONObject().put("scope",new JSONObject().put("type","user")).put("scopeRevision",0).put("scopeLabel","QA only"));
   store.uiGranted(true);store.uiResume(false);store.enabled(true);
   runOnMainSync(()->c.startForegroundService(new Intent(c,DeviceChannelService.class)));
   result.putString("result","test pairing configured");sendStatus(0,result);
   startActivitySync(new Intent(c,dev.claude.assistant.remote.UiControlActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(600000);finish(-1,result);
  }catch(Exception e){result.putString("error",e.getClass().getSimpleName());finish(0,result);}
 }
}
