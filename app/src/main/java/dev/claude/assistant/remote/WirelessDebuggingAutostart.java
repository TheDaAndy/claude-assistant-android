package dev.claude.assistant.remote;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.*;
import android.provider.Settings;
import dev.claude.assistant.ankai.DebugAutostartPolicy;

/** Requires an explicit development-permission grant. Does not bypass Android network consent. */
final class WirelessDebuggingAutostart {
 static boolean permitted(Context context){return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
 static boolean enableIfNeeded(Context context,DeviceLinkStore store){
  try{
   int boot=Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);
   boolean enabled=Settings.Global.getInt(context.getContentResolver(),"adb_wifi_enabled",0)==1;
   boolean consent=store.enabled()&&store.id()!=null&&store.maximumRequested()&&store.autoDebugEnabled();
   // Existing debugging already enabled by Android/user counts as handled for this boot.
   if(consent&&enabled&&boot>=0){store.autoDebugBoot(boot);return false;}
   ConnectivityManager network=context.getSystemService(ConnectivityManager.class);boolean wifi=false;
   for(Network n:network.getAllNetworks()){NetworkCapabilities capabilities=network.getNetworkCapabilities(n);if(capabilities!=null&&capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=true;break;}}
   if(!DebugAutostartPolicy.allowed(consent,permitted(context),wifi,enabled,boot,store.autoDebugBoot()))return false;
   if(Settings.Global.putInt(context.getContentResolver(),"adb_wifi_enabled",1)){store.autoDebugBoot(boot);return true;}
  }catch(SecurityException|IllegalArgumentException denied){/* No privilege escalation or UI fallback. */}
  return false;
 }
}
