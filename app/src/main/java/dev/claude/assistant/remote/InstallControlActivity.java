package dev.claude.assistant.remote;
import android.app.*;import android.os.Bundle;import android.content.*;import android.graphics.Color;import android.net.Uri;import android.provider.Settings;import android.widget.*;
public final class InstallControlActivity extends Activity {
 private DeviceLinkStore store;private TextView status;private CheckBox grant;private boolean rendering;
 private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,20,0,20);return t;}
 @Override public void onCreate(Bundle b){super.onCreate(b);store=new DeviceLinkStore(this);ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Color.rgb(26,26,26));LinearLayout l=new LinearLayout(this);l.setOrientation(1);int p=(int)(16*getResources().getDisplayMetrics().density);l.setPadding(p,p,p,p);sc.addView(l);setContentView(sc);
  l.addView(text("Apps über Ankaï installieren",24));l.addView(text("Die KI kann ein gebautes Einzel-APK über deinen geschützten Gerätekanal übertragen. Größe und SHA256 werden geprüft. Du bestätigst jede Installation im Android-Dialog – keine stille Installation, kein Root nötig.",16));
  l.addView(text("Freigabe: "+store.scopeLabel()+". Installationsanfragen sind unabhängig von der Bildschirmsteuerung. Pausieren oder Widerrufen bricht noch offene Anfragen soweit möglich ab. Bereits bestätigte Installationen werden nicht rückgängig gemacht.",16));
  status=text("",16);l.addView(status);grant=new CheckBox(this);grant.setTextColor(Color.WHITE);grant.setText("APK-Installationsanfragen für Ankaï erlauben");l.addView(grant);
  grant.setOnCheckedChangeListener((v,on)->{if(rendering)return;if(!on){store.installGranted(false);PackageInstallManager.cancelAll(this,"Installationsfreigabe entzogen");refresh();DevicePresenceMonitor.changed();return;}
   new AlertDialog.Builder(this).setTitle("Installationsanfragen erlauben?").setMessage("Berechtigte Aufträge innerhalb deiner Gerätefreigabe dürfen APKs auf dieses Handy übertragen und Androids Installationsbestätigung anfordern. Du bestätigst am Handy selbst. Bis100MiB, nur Einzel-APKs. Keine automatische Deinstallation oder Sperrumgehung.")
    .setNegativeButton("Abbrechen",(d,w)->refresh()).setPositiveButton("Erlauben",(d,w)->{store.installGranted(true);refresh();DevicePresenceMonitor.changed();}).setOnCancelListener(d->refresh()).show();
  });
  Button permission=new Button(this);permission.setText("Android: Apps aus dieser Quelle zulassen");permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))));l.addView(permission);
  l.addView(text("Zusätzlich Android-Erlaubnis oben aktivieren, Benachrichtigungen erlauben und den Gerätekanal einschalten. Bei einer Anfrage die Ankaï-Benachrichtigung antippen und Androids App-Name/Installation prüfen. Erst eine bestätigte Android-Erfolgsmeldung gilt als installiert. Auf gesperrtem Handy zunächst entsperren.",14));
  Button back=new Button(this);back.setText("Zurück");back.setOnClickListener(v->finish());l.addView(back);refresh();
 }
 private void refresh(){rendering=true;grant.setChecked(store.installGranted());grant.setEnabled(store.id()!=null);rendering=false;status.setText(PackageInstallManager.ready(this)?"Android-Erlaubnis und Benachrichtigungen bereit":"Android-Erlaubnis oder Benachrichtigungen fehlen");}
 @Override public void onResume(){super.onResume();if(status!=null){refresh();DevicePresenceMonitor.changed();}}
}
