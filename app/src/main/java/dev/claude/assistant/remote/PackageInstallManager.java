package dev.claude.assistant.remote;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;

/** Single APK, verified transfer, platform confirmation, durable callback outbox. */
public final class PackageInstallManager {
 static final String CHANNEL="device_installs";
 private static final Object LOCK=new Object();
 public static boolean ready(Context c){return c.getPackageManager().canRequestPackageInstalls()&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
 private static boolean permitted(Context c,DeviceLinkStore s,long revision){return s.id()!=null&&s.enabled()&&s.installGranted()&&ready(c)&&s.scopeRevision()==revision;}
 private static JSONObject result(String status,Object data)throws Exception{return new JSONObject().put("status",status).put("data",data);}
 static JSONObject begin(Context c,JSONObject command,DeviceHttp http)throws Exception{
  File[] old=c.getCacheDir().listFiles();if(old!=null)for(File f:old)if(f.getName().startsWith("ankai-install-")&&System.currentTimeMillis()-f.lastModified()>900000)f.delete();
  DeviceLinkStore s=new DeviceLinkStore(c);String id=command.getString("id");long rev=command.getLong("scopeRevision");
  if(!permitted(c,s,rev))return result("error","APK-Installation lokal nicht freigegeben oder Android-Erlaubnis fehlt.");
  synchronized(LOCK){JSONObject jobs=s.installJobs();if(jobs.length()>=16){java.util.Iterator<String> keys=jobs.keys();while(keys.hasNext()){String key=keys.next();JSONObject j=jobs.getJSONObject(key);if(terminal(j)&&j.optBoolean("reported"))s.removeInstallJob(key);}}}
  if(s.installJobs().length()>=16)return result("error","Zu viele gespeicherte Installationen; Gerätekanal neu prüfen.");
  File apk=File.createTempFile("ankai-install-",".apk",c.getCacheDir());int sessionId=-1;
  try {
   JSONObject p=command.getJSONObject("params");long size=p.getLong("size");String sha=p.getString("sha256");
   http.downloadPackage(s,p.getString("packageId"),apk,size,sha);
   if(!permitted(c,s,rev)||command.getLong("expiresAt")<=System.currentTimeMillis())throw new IOException("Installation pausiert/abgelaufen.");
   PackageInfo info=c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0);if(info==null)throw new IOException("APK ist nicht installierbar.");
   PackageInstaller installer=c.getPackageManager().getPackageInstaller();
   PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
   params.setSize(size);params.setAppPackageName(info.packageName);
   if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
   sessionId=installer.createSession(params);
   JSONObject job=new JSONObject().put("deviceId",s.id()).put("scopeRevision",rev).put("sessionId",sessionId).put("expiresAt",command.getLong("expiresAt"))
    .put("packageName",info.packageName).put("versionCode",Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode).put("sha256",sha)
    .put("requester",command.optString("requester")).put("chatTitle",command.optString("chatTitle")).put("reported",false);
   synchronized(LOCK){s.installJob(id,job);}
   try(PackageInstaller.Session session=installer.openSession(sessionId)){
    try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,size)){byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);session.fsync(out);}
    if(!permitted(c,s,rev))throw new IOException("Installationsfreigabe geändert.");
    Intent callback=new Intent(c,InstallResultReceiver.class).setAction("dev.claude.assistant.INSTALL_RESULT").setData(Uri.parse("ankai-install:"+id));
    PendingIntent receiver=PendingIntent.getBroadcast(c,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
    session.commit(receiver.getIntentSender());
   }
   return result("confirmation_required",new JSONObject().put("packageName",info.packageName).put("sha256",sha).put("message","APK übertragen. Android-Bestätigung am Handy erforderlich; noch kein Installationserfolg."));
  }catch(Exception e){
   if(sessionId>=0)try{c.getPackageManager().getPackageInstaller().abandonSession(sessionId);}catch(Exception ignored){}
   JSONObject failure=result("error","APK-Übertragung/Installation fehlgeschlagen oder abgebrochen. Keine automatische Wiederholung.");
   if(s.installJob(id)!=null)saveResult(c,id,failure);
   return failure;
  }finally{apk.delete();}
 }
 static void callback(Context c,Intent intent)throws Exception{
  if(intent.getData()==null)return;String id=intent.getData().getSchemeSpecificPart();DeviceLinkStore s=new DeviceLinkStore(c);
  JSONObject job=s.installJob(id);if(job==null||job.getInt("sessionId")!=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1))return;
  int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
   if(!permitted(c,s,job.getLong("scopeRevision"))||job.getLong("expiresAt")<=System.currentTimeMillis()){cancel(c,id,job,"Installationsfreigabe geändert/abgelaufen.");return;}
   Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm==null){cancel(c,id,job,"Android-Installationsbestätigung fehlt.");return;}
   NotificationManager n=c.getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel(CHANNEL,"APK-Installationen",NotificationManager.IMPORTANCE_DEFAULT));
   PendingIntent open=PendingIntent.getActivity(c,job.getInt("sessionId"),confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   n.notify(DeviceChannelService.notificationId(id),new Notification.Builder(c,CHANNEL).setSmallIcon(dev.claude.assistant.R.drawable.ic_assistant)
    .setContentTitle("APK installieren: "+job.getString("packageName")).setContentText(job.optString("requester")+" · Antippen und in Android bestätigen")
    .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(Math.max(1,job.getLong("expiresAt")-System.currentTimeMillis())).build());
   saveResult(c,id,result("confirmation_required",new JSONObject().put("packageName",job.getString("packageName")).put("message","Android wartet auf deine Installationsbestätigung am Handy.")));
  }else{
   c.getSystemService(NotificationManager.class).cancel(DeviceChannelService.notificationId(id));
   JSONObject data=new JSONObject().put("packageName",job.getString("packageName")).put("versionCode",job.getLong("versionCode")).put("sha256",job.getString("sha256"))
    .put("installed",status==PackageInstaller.STATUS_SUCCESS).put("androidStatus",status).put("message",status==PackageInstaller.STATUS_SUCCESS?"Android hat die Installation bestätigt.":"Android hat die Installation abgebrochen/abgelehnt.");
   saveResult(c,id,result(status==PackageInstaller.STATUS_SUCCESS?"ok":"error",data));
  }
 }
 private static void saveResult(Context c,String id,JSONObject value)throws Exception{
  synchronized(LOCK){DeviceLinkStore s=new DeviceLinkStore(c);JSONObject job=s.installJob(id);if(job==null)return;
   JSONObject previous=job.optJSONObject("result");if(previous!=null&&!"confirmation_required".equals(previous.optString("status")))return;
   job.put("result",value).put("reported",false);s.installJob(id,job);
  }
 }
 private static boolean terminal(JSONObject job){JSONObject r=job.optJSONObject("result");return r!=null&&!"confirmation_required".equals(r.optString("status"));}
 private static void cancel(Context c,String id,JSONObject job,String reason)throws Exception{
  try{c.getPackageManager().getPackageInstaller().abandonSession(job.getInt("sessionId"));}catch(Exception ignored){}
  c.getSystemService(NotificationManager.class).cancel(DeviceChannelService.notificationId(id));saveResult(c,id,result("error",reason));
 }
 public static void cancelAll(Context c,String reason){try{DeviceLinkStore s=new DeviceLinkStore(c);JSONObject jobs=s.installJobs();java.util.Iterator<String> keys=jobs.keys();while(keys.hasNext()){String id=keys.next();JSONObject j=jobs.getJSONObject(id);if(!terminal(j))cancel(c,id,j,reason);}}catch(Exception ignored){}}
 static void flush(Context c){
  DeviceLinkStore s=new DeviceLinkStore(c);JSONObject jobs=s.installJobs();java.util.Iterator<String> keys=jobs.keys();
  while(keys.hasNext())try{
   String id=keys.next();JSONObject job=jobs.getJSONObject(id);
   if(!terminal(job)&&(job.getLong("expiresAt")<=System.currentTimeMillis()||!job.getString("deviceId").equals(s.id())||job.getLong("scopeRevision")!=s.scopeRevision()))cancel(c,id,job,"Installationsauftrag abgelaufen/Freigabe geändert.");
   job=s.installJob(id);if(job==null)continue;
   JSONObject value=job.optJSONObject("result");if(value!=null&&!job.optBoolean("reported")&&job.optString("deviceId").equals(s.id())){
    boolean delivered=false;try{new DeviceHttp().channel(s,"/commands/"+id+"/result","POST",value);delivered=true;}catch(DeviceHttp.Failure failure){delivered=failure.status==404||failure.status==409||failure.status==401;}
    if(delivered)synchronized(LOCK){JSONObject latest=s.installJob(id);if(latest!=null&&value.toString().equals(latest.optJSONObject("result").toString())){latest.put("reported",true);s.installJob(id,latest);}}
   }
  }catch(Exception ignored){}
 }
}
