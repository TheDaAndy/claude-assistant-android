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
