package dev.claude.assistant.remote;
import android.app.*;
import android.os.*;
import android.content.*;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;

public final class UiControlActivity extends Activity {
    private DeviceLinkStore store;private TextView status;private CheckBox grant,resume;private boolean rendering;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,dp(8),0,dp(8));return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new DeviceLinkStore(this);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Color.rgb(26,26,26));LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(16),dp(16),dp(16),dp(24));sc.addView(l);setContentView(sc);
        l.addView(text("Handy steuern ohne Root",24));
        l.addView(text("Ankaï Assistant vermittelt zwischen deinem Server und der Android-Bedienungshilfe. Kein Shizuku, Root oder WLAN-Debugging nötig.",16));
        l.addView(text("1. In Android „Ankaï-Gerätesteuerung“ aktivieren.\n2. Unten die Fernsteuerung bestätigen.\n3. Zurück zur Kopplung und den Gerätekanal einschalten.",16));
        Button settings=new Button(this);settings.setText("Bedienungshilfe in Android öffnen");settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));l.addView(settings);
        status=text("",16);l.addView(status);
        grant=new CheckBox(this);grant.setText("Bildschirm und Bedienung für Ankaï freigeben");grant.setTextColor(Color.WHITE);l.addView(grant);
        resume=new CheckBox(this);resume.setText("Nach Neustart / Wiederverbindung automatisch fortsetzen");resume.setTextColor(Color.WHITE);l.addView(resume);
        resume.setOnCheckedChangeListener((v,on)->{if(!rendering)store.uiResume(on);});
        grant.setOnCheckedChangeListener((v,on)->{if(rendering)return;if(!on){pause();store.uiGranted(false);refresh();return;}
            new AlertDialog.Builder(this).setTitle("Fernsteuerung freigeben?").setMessage("Freigabe: "+store.scopeLabel()+"\n\nBerechtigte Ankaï-Aufträge können Bildschirmtexte und Screenshots lesen, Apps öffnen, tippen, wischen und Text eingeben. Innerhalb deines Auftrags ohne Einzelabfragen. Keine Shell-/Root-Rechte. Passwortfelder, Gerätesperre und geschützte Ansichten bleiben eingeschränkt.\n\nJederzeit über die Benachrichtigung pausieren oder hier widerrufen.")
            .setNegativeButton("Abbrechen",(d,w)->refresh()).setPositiveButton("Freigeben",(d,w)->{pause();store.uiGranted(true);refresh();}).setOnCancelListener(d->refresh()).show();});
        l.addView(text("Die automatische Wiederaufnahme ist freiwillig. Pausieren schaltet sie aus. Android kann Netzwerkzugriff im Energiesparmodus verzögern; nach Force-Stop musst du die App wieder öffnen. Nach einem Neustart kann erst das Entsperren nötig sein.",14));
        l.addView(text("Keine beliebigen Systemlogs oder privaten App-Dateien. Android-Freigaben und Installationsbestätigungen erfolgen lokal. Bei Spielen fehlen oft lesbare UI-Elemente; dafür Screenshots und Koordinaten verwenden.",14));
        Button back=new Button(this);back.setText("Zurück zur Kopplung");back.setOnClickListener(v->finish());l.addView(back);refresh();
    }
    private void pause(){store.uiResume(false);store.enabled(false);stopService(new Intent(this,DeviceChannelService.class));}
    private void refresh(){if(status==null)return;boolean connected=UiControlService.ready();status.setText(connected?"Bedienungshilfe verbunden · ohne Root/ADB":"Noch nicht verbunden · in Android aktivieren, dann hierher zurückkehren");rendering=true;grant.setChecked(store.uiGranted());grant.setEnabled(connected&&store.id()!=null);resume.setChecked(store.uiResume());resume.setEnabled(store.uiGranted());rendering=false;}
    @Override public void onResume(){super.onResume();refresh();}
}
