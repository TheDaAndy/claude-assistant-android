package dev.claude.assistant.remote;
import android.content.*;
/** Explicit, non-exported PackageInstaller callback; receiver survives activity changes. */
public final class InstallResultReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  PendingResult pending=goAsync();
  new Thread(()->{try{PackageInstallManager.callback(context,intent);}catch(Exception ignored){}finally{pending.finish();}PackageInstallManager.flush(context);},"install-result").start();
 }
}
