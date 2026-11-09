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
 private void clickText(android.view.View v,String text){
  if(v instanceof android.widget.Button && text.contentEquals(((android.widget.Button)v).getText())){v.performClick();return;}
  if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)clickText(((android.view.ViewGroup)v).getChildAt(i),text);
 }
 private Bundle args;
 @Override public void onCreate(Bundle args){this.args=args;start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try {
   Context c=getTargetContext();
   if(Boolean.parseBoolean(args.getString("cardSync","false"))){
    dev.claude.assistant.storage.EncryptedPrefsSecretStore.connectionStore(c).save(new dev.claude.assistant.ankai.AnkaiConnection(args.getString("baseUrl"),"qa","fixture",null,null,null));
    android.app.Activity activity=startActivitySync(new Intent(c,dev.claude.assistant.DecisionsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    java.lang.reflect.Field field=activity.getClass().getDeclaredField("dashboard");field.setAccessible(true);
    long deadline=android.os.SystemClock.elapsedRealtime()+15000;
    while(field.get(activity)==null && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
    if(field.get(activity)==null)throw new IllegalStateException("No dashboard");
    waitForIdleSync();runOnMainSync(()->clickText(activity.getWindow().getDecorView(),"Antworten"));
    deadline=android.os.SystemClock.elapsedRealtime()+2500;
    while(((dev.claude.assistant.ankai.PersonalDashboard)field.get(activity)).openCount()!=0 && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
    if(((dev.claude.assistant.ankai.PersonalDashboard)field.get(activity)).openCount()!=0)throw new IllegalStateException("Card stayed open while slow refresh blocked");
    waitForIdleSync();android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();
    try(java.io.FileOutputStream out=new java.io.FileOutputStream("/sdcard/Download/card-sync-verified.png")){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
    result.putString("result","Saved answer disappeared before delayed dashboard GET completed");finish(-1,result);return;
   }
   if(Boolean.parseBoolean(args.getString("authRecovery","false"))){
    DeviceLinkStore device=new DeviceLinkStore(c);
    device.save("auth-qa-device","dummy-device-token",args.getString("baseUrl"),"QA only");device.enabled(false);
    dev.claude.assistant.ankai.AnkaiConnectionStore account=dev.claude.assistant.storage.EncryptedPrefsSecretStore.connectionStore(c);
    account.save(new dev.claude.assistant.ankai.AnkaiConnection(args.getString("baseUrl"),"qa","dummy-password","p1","QA Project","ankai_session=expired"));
    android.app.Activity activity=startActivitySync(new Intent(c,dev.claude.assistant.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    long deadline=android.os.SystemClock.elapsedRealtime()+20000;
    while(!"ankai_session=qa_fresh".equals(account.load().sessionCookie) && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
    if(!"ankai_session=qa_fresh".equals(account.load().sessionCookie))throw new IllegalStateException("Cookie not renewed");
    if(!"p1".equals(account.load().defaultProjectId)||!"auth-qa-device".equals(device.id()))throw new IllegalStateException("Project/pairing changed");
    waitForIdleSync();runOnMainSync(()->{
      android.view.View error=activity.findViewById(c.getResources().getIdentifier("ankai_connection_error","id",c.getPackageName()));
      if(error.getVisibility()==android.view.View.VISIBLE)throw new IllegalStateException("Login error still visible");
    });
    result.putString("result","expired session renewed; cookie persisted; project and pairing preserved; no visible auth error");finish(-1,result);return;
   }
   if(Boolean.parseBoolean(args.getString("inspectRecovery","false"))){
    DeviceLinkStore existing=new DeviceLinkStore(c);
    if(!existing.enabled())throw new IllegalStateException("Connection disabled after process death");
    runOnMainSync(()->c.startService(new Intent(c,DeviceChannelService.class).setAction(DeviceChannelService.STOP)));
    Thread.sleep(1000);
    if(existing.enabled())throw new IllegalStateException("Explicit pause did not disable connection");
    result.putString("result","recovery retained consent; explicit pause disabled connection");finish(-1,result);return;
   }
   startActivitySync(new Intent(c,dev.claude.assistant.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   DeviceLinkStore store=new DeviceLinkStore(c);
   store.save(args.getString("deviceId"),args.getString("token"),args.getString("baseUrl"),"QA only");
   store.updateDevice(new JSONObject().put("scope",new JSONObject().put("type","user")).put("scopeRevision",0).put("scopeLabel","QA only"));
   store.uiGranted(!Boolean.parseBoolean(args.getString("install","false")));store.installGranted(Boolean.parseBoolean(args.getString("install","false")));store.uiResume(false);store.enabled(true);
   runOnMainSync(()->c.startForegroundService(new Intent(c,DeviceChannelService.class)));
   result.putString("result","test pairing configured");sendStatus(0,result);
   if(Boolean.parseBoolean(args.getString("recovery","false"))){Thread.sleep(1000);finish(-1,result);return;}
   startActivitySync(new Intent(c,Boolean.parseBoolean(args.getString("install","false"))?dev.claude.assistant.remote.InstallControlActivity.class:dev.claude.assistant.remote.UiControlActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(600000);finish(-1,result);
  }catch(Exception e){result.putString("error",e.getClass().getSimpleName());finish(0,result);}
 }
}
