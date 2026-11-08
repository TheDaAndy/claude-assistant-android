package dev.claude.assistant.remote;

import android.app.*;
import android.os.*;
import android.content.*;
import android.provider.Settings;
import android.graphics.Color;
import android.text.InputType;
import android.widget.*;
import java.util.concurrent.*;

/** Local pairing consent stays on the phone. Pairing codes are never persisted or sent to Ankai. */
public final class MaximumControlActivity extends Activity {
    private DeviceLinkStore store;
    private TextView status;
    private CheckBox maximum;
    private EditText connectionPort;
    private Button pair,connect,disconnect;
    private boolean rendering,busy;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(8),0,dp(8));return t;}
    private EditText field(LinearLayout l,String label,boolean secret){
        l.addView(text(label,16));EditText e=new EditText(this);e.setTextColor(Color.WHITE);e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|(secret?InputType.TYPE_NUMBER_VARIATION_PASSWORD:0));
        e.setSaveEnabled(false);if(secret)e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);l.addView(e);return e;
    }
    private Button button(LinearLayout l,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());l.addView(b);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DeviceLinkStore(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(26,26,26));LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(16),dp(16),dp(16),dp(24));scroll.addView(l);setContentView(scroll);
        l.addView(text("Wireless Debugging",24));
        l.addView(text("Direkt in Ankaï integriert · keine zusätzliche App nötig. Android 11 oder neuer und aktiviertes WLAN-Debugging sind erforderlich. Die Verbindung bleibt lokal auf diesem Handy.",16));
        button(l,"Entwickleroptionen öffnen",()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}});
        l.addView(text("1. ›Kopplung per Benachrichtigung starten‹ antippen.\n2. In den Entwickleroptionen WLAN-Debugging einschalten und ›Mit Kopplungscode koppeln‹ öffnen.\n3. Benachrichtigungen herunterziehen und bei Ankaï ›Code eingeben‹ wählen. Sechsstelligen Code senden. Androids Kopplungsdialog bleibt geöffnet.\n4. Ankaï verbindet sich automatisch. Danach hier den ADB-Zugriff freigeben.",16));
        pair=button(l,"Kopplung per Benachrichtigung starten",this::startNotificationPairing);
        l.addView(text("Kein geteilter Bildschirm nötig. Ports werden automatisch erkannt. Falls die Portsuche nicht funktioniert, in der Benachrichtigung den angezeigten Kopplungsport, ein Leerzeichen und den Code eingeben.",16));
        l.addView(text("Bereits gekoppelt? Bei Bedarf den Verbindungsport von der WLAN-Debugging-Hauptseite hier eingeben.",16));
        connectionPort=field(l,"Verbindungsport",false);
        connect=button(l,"Lokal verbinden",()->{
            final int port;try{port=port(connectionPort);}catch(Exception e){status.setText("Verbindungsport muss zwischen 1 und 65535 liegen.");return;}
            work(()->{WirelessAdbConnection c=WirelessAdbConnection.get(this);c.disconnect();if(!c.connect("127.0.0.1",port))throw new Exception();c.verifyShell();},"Verbunden · UID "+WirelessAdbBridge.privilegeUid());
        });
        disconnect=button(l,"Verbindung trennen",()->{pause();work(()->WirelessAdbConnection.get(this).disconnect(),"Verbindung getrennt.");});
        status=text("",16);l.addView(status);
        maximum=new CheckBox(this);maximum.setText("ADB-Zugriff für die Gerätefreigabe erlauben");maximum.setTextColor(Color.WHITE);l.addView(maximum);
        maximum.setOnCheckedChangeListener((b,on)->{
            if(rendering)return;
            if(!on){pause();store.maximum(false,-1);refresh();return;}
            if(!WirelessAdbBridge.authorized()){refresh();return;}
            final int consentUid=WirelessAdbBridge.privilegeUid();
            new AlertDialog.Builder(this).setTitle("ADB-Zugriff erlauben?")
                .setMessage("Freigabe: "+store.scopeLabel()+"\n\n"+(consentUid==0?"ROOT (UID 0): weitreichende Root-Befehle möglich. ":"ADB/SHELL: ")+" Ankaï kann Logs lesen, Apps und Bedienung steuern sowie Einstellungen ändern. Android- und SELinux-Beschränkungen bleiben bestehen. Bei Root können auch private App-Daten zugänglich sein; ohne Root bleiben sie geschützt.\n\nBefehle laufen im Rahmen deines Auftrags ohne Einzelbestätigung. Der sichtbare Gerätekanal muss aktiv sein. Du kannst jederzeit pausieren oder widerrufen.")
                .setNegativeButton("Abbrechen",(d,w)->refresh()).setPositiveButton("Freigeben",(d,w)->{if(!WirelessAdbBridge.authorized()||!dev.claude.assistant.ankai.AdbUidPolicy.matches(consentUid,WirelessAdbBridge.privilegeUid())){refresh();return;}pause();store.maximum(true,consentUid);refresh();})
                .setOnCancelListener(d->refresh()).show();
        });
        l.addView(text("Bei aktivem Gerätekanal und erteiltem ADB-Zugriff wird die bestehende Kopplung nach Prozessende, Neustart und WLAN-Rückkehr automatisch erneut verbunden, sofern Android WLAN-Debugging freigibt. Ohne die unten beschriebene Sonderberechtigung musst du ausgeschaltetes WLAN-Debugging in Android wieder einschalten. Eine bestehende Kopplung bleibt erhalten, bis sie in Android widerrufen wird. Trennen beendet die Verbindung; zum endgültigen Widerruf Ankaï in Android unter ›Gekoppelte Geräte‹ vergessen. Root wird nur genutzt, wenn der lokale ADB-Dienst tatsächlich UID 0 bereitstellt und du diese Rechte freigibst. Bereits gestartete Befehle können bis zum Zeitlimit weiterlaufen.",14));
        CheckBox autoDebug=new CheckBox(this);autoDebug.setText("WLAN-Debugging nach Neustart automatisch einschalten");autoDebug.setTextColor(Color.WHITE);autoDebug.setChecked(store.autoDebugEnabled());
        autoDebug.setOnCheckedChangeListener((b,on)->store.autoDebugEnabled(on));l.addView(autoDebug);
        l.addView(text(WirelessDebuggingAutostart.permitted(this)?"Sonderberechtigung vorhanden. Bei aktivem Gerätekanal und ADB-Freigabe einmal pro Neustart; WLAN-Vertrauen und erstes Entsperren bleiben Androids Entscheidung.":"Benötigt einmalig die ADB-Sonderfreigabe WRITE_SECURE_SETTINGS. Ohne sie wird keine Systemeinstellung geändert. Nach dem automatischen Einschalten bleibt ein manuelles Ausschalten bis zum nächsten Neustart wirksam.",14));
        button(l,"Zurück zur Gerätefreigabe",this::finish);refresh();
    }
    private void startNotificationPairing(){
        if(Build.VERSION.SDK_INT<30)return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},7304);return;
        }
        NotificationManager notifications=getSystemService(NotificationManager.class);
        NotificationChannel channel=notifications.getNotificationChannel("wireless_pairing");
        if(!notifications.areNotificationsEnabled()||(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)){
            status.setText("Bitte Benachrichtigungen für Ankaï erlauben, damit die Code-Eingabe verfügbar ist.");
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));return;
        }
        startForegroundService(new Intent(this,WirelessPairingService.class).setAction(WirelessPairingService.START));
        try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==7304){if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)startNotificationPairing();
            else status.setText("Für die Code-Eingabe bitte Benachrichtigungen erlauben und erneut starten.");}
    }
    private int port(EditText input){int p=Integer.parseInt(input.getText().toString().trim());if(p<1||p>65535)throw new IllegalArgumentException();return p;}
    private interface Job{void run()throws Exception;}
    private void work(Job job,String success){
        if(busy)return;busy=true;refresh();status.setText("Verbindung wird bearbeitet …");
        worker.submit(()->{boolean ok=false;String detail="";try{job.run();ok=true;}catch(Exception failure){detail=WirelessAdbConnection.failureMessage(failure);}final boolean passed=ok;final String error=detail;
            runOnUiThread(()->{if(isDestroyed())return;busy=false;refresh();status.setText(passed?(WirelessAdbBridge.authorized()?"Verbunden · UID "+WirelessAdbBridge.privilegeUid():success):error);DevicePresenceMonitor.changed();});});
    }
    private void pause(){store.uiResume(false);store.enabled(false);stopService(new Intent(this,DeviceChannelService.class));DevicePresenceMonitor.changed();}
    private void refresh(){
        if(status==null||isDestroyed())return;boolean ready=WirelessAdbBridge.authorized(),supported=Build.VERSION.SDK_INT>=30;
        status.setText(!supported?"Wireless Debugging benötigt Android 11 oder neuer.":ready?"Verbunden · UID "+WirelessAdbBridge.privilegeUid():"Nicht verbunden · Kopplung per Benachrichtigung starten.");
        pair.setEnabled(supported&&!busy);connect.setEnabled(supported&&!busy);disconnect.setEnabled(!busy&&WirelessAdbBridge.running());
        rendering=true;maximum.setChecked(store.maximumGranted(WirelessAdbBridge.privilegeUid()));maximum.setEnabled(ready&&!busy&&store.id()!=null);rendering=false;
    }
    @Override public void onResume(){super.onResume();refresh();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
