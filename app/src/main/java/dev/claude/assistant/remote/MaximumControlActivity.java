package dev.claude.assistant.remote;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;
import rikka.shizuku.Shizuku;
import android.content.pm.PackageManager;

public final class MaximumControlActivity extends Activity {
    private DeviceLinkStore store;
    private TextView status;
    private CheckBox maximum;
    private Button permission;
    private boolean rendering;
    private final Shizuku.OnBinderReceivedListener received=()->runOnUiThread(this::refresh);
    private final Shizuku.OnBinderDeadListener dead=()->runOnUiThread(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener result=(code,value)->runOnUiThread(this::refresh);
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(8),0,dp(8));return t;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DeviceLinkStore(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(26,26,26));LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(l);setContentView(scroll);
        l.addView(text("Maximale Gerätefreigabe",24));
        l.addView(text("Ankaï nutzt die tatsächlichen Shizuku-Rechte lokal auf dem Handy. Der Server braucht keine direkte WLAN-Verbindung zum Handy.",16));
        l.addView(text("1. Shizuku installieren/öffnen.\n2. Dort über WLAN-Debugging koppeln und Shizuku starten.\n3. Ankaï in Shizuku erlauben.\n4. Maximalmodus freigeben und den Gerätekanal aktivieren.",16));
        Button manager=new Button(this);manager.setText("Shizuku installieren / öffnen");manager.setOnClickListener(v->{Intent i=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(i==null)i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/"));startActivity(i);});l.addView(manager);
        Button developer=new Button(this);developer.setText("Entwickleroptionen öffnen");developer.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException unavailable){startActivity(new Intent(Settings.ACTION_SETTINGS));}});l.addView(developer);
        status=text("",16);l.addView(status);
        permission=new Button(this);permission.setText("Ankaï in Shizuku freigeben");permission.setOnClickListener(v->{try{Shizuku.requestPermission(1301);}catch(Exception unavailable){refresh();}});l.addView(permission);
        maximum=new CheckBox(this);maximum.setText("Maximalmodus für die Gerätefreigabe erlauben");maximum.setTextColor(Color.WHITE);l.addView(maximum);
        maximum.setOnCheckedChangeListener((b,on)->{
            if(rendering)return;
            if(!on){pause();store.maximum(false,-1);refresh();return;}
            int uid=ShizukuBridge.privilegeUid();
            if(uid!=0 && uid!=2000){refresh();return;}
            new AlertDialog.Builder(this).setTitle("Maximalfreigabe erlauben?")
                .setMessage("Freigabe: "+store.scopeLabel()+"\n\n"+(uid==0?"ROOT: Ankaï erhält die verfügbaren Root-Rechte. Auch private App-Daten und weitreichende Änderungen können zugänglich sein.":"ADB/SHELL: Ankaï kann Logs lesen, Apps/Bedienung steuern und mit diesen Rechten Einstellungen ändern. Android-/SELinux-Beschränkungen bleiben bestehen; dies ist kein Root-Zugang.")+"\n\nBefehle werden ohne Einzelbestätigung ausgeführt, soweit dein aktueller Auftrag sie erlaubt. Der sichtbare Gerätekanal muss aktiv sein. Du kannst jederzeit pausieren oder widerrufen.")
                .setNegativeButton("Abbrechen",(d,w)->refresh()).setPositiveButton("Freigeben",(d,w)->{pause();store.maximum(true,uid);refresh();})
                .setOnCancelListener(d->refresh()).show();
        });
        l.addView(text("Ein klassischer Geräteadministrator ersetzt keine ADB-Rechte. Ohne Root muss Shizuku nach einem Handy-Neustart erneut gestartet werden. Für diese App gibt es keinen automatischen Root- oder Device-Owner-Zugang.",14));
        Button done=new Button(this);done.setText("Zurück zur Kopplung");done.setOnClickListener(v->finish());l.addView(done);
        Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(dead);Shizuku.addRequestPermissionResultListener(result);refresh();
    }
    private void pause(){store.enabled(false);stopService(new Intent(this,DeviceChannelService.class));}
    private void refresh(){
        if(status==null || isDestroyed())return;
        boolean running=ShizukuBridge.running(),authorized=ShizukuBridge.authorized();int uid=ShizukuBridge.privilegeUid();
        status.setText(!running?"Shizuku nicht gestartet · erst Schritt 1 und 2 abschließen":!authorized?"Shizuku läuft · Ankaï braucht deine Freigabe":uid==0?"Shizuku bereit · ROOT (UID 0)":"Shizuku bereit · ADB/SHELL (UID "+uid+")");
        if(authorized && store.maximumRequested() && !store.maximumGranted(uid))status.append("\nRechtestufe geändert · Maximalfreigabe erneut bestätigen");
        permission.setEnabled(running && !authorized);rendering=true;
        maximum.setChecked(store.maximumGranted(uid));maximum.setEnabled(authorized && store.id()!=null);rendering=false;
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override public void onDestroy(){Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(dead);Shizuku.removeRequestPermissionResultListener(result);super.onDestroy();}
}
