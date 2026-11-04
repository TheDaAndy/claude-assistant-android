package dev.claude.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import dev.claude.assistant.ankai.*;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;

/** Native decision inbox. Policy and execution stay on the linked Ankaï server. */
public final class DecisionsActivity extends Activity {
    private static final int BG=0xff131b18,SURFACE=0xff202b25,TEXT=0xfff0f3ee,MUTED=0xffbbc8be,ACCENT=0xffeaa77d;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,String> drafts=new HashMap<>(),choices=new HashMap<>();
    private final List<View> controls=new ArrayList<>();
    private AnkaiConnectionStore store;
    private PersonalDashboard dashboard;
    private LinearLayout cards;
    private TextView subtitle,status;
    private Button openTab,historyTab,refresh;
    private boolean busy,resumed,history;
    private int generation;
    private MediaRecorder recorder;
    private File recordingFile;
    private PersonalDecision recordingDecision;
    private Button recordingButton;
    private final Runnable poll=new Runnable(){public void run(){if(resumed){if(!busy&&recorder==null&&!editing())load(false);handler.postDelayed(this,15000);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=EncryptedPrefsSecretStore.connectionStore(this);
        if(state!=null){history=state.getBoolean("history");restore(state,"draft",drafts);restore(state,"choice",choices);}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(20),dp(12),dp(20),0);setContentView(root);
        LinearLayout header=row();TextView brand=label("Ankaï Assistant",18,TEXT,true);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("Einstellungen",false,()->startActivity(new Intent(this,MainActivity.class))));root.addView(header);
        root.addView(label("Deine Entscheidungen",27,TEXT,true));subtitle=label("Vorschläge, die deine Ziele weiterbringen.",14,MUTED,false);root.addView(subtitle);
        LinearLayout tabs=row();tabs.setPadding(0,dp(14),0,dp(4));openTab=button("Offen",false,()->{history=false;render();});historyTab=button("Verlauf",false,()->{history=true;render();});refresh=button("Aktualisieren",false,()->load(true));
        tabs.addView(openTab,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(historyTab,new LinearLayout.LayoutParams(0,-2,1));tabs.addView(refresh);root.addView(tabs);
        status=label("",14,MUTED,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);showStatus("");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);cards=column();cards.setPadding(0,dp(10),0,dp(24));scroll.addView(cards);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    @Override protected void onResume(){super.onResume();resumed=true;load(true);handler.postDelayed(poll,15000);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(poll);discardRecording();super.onPause();}
    @Override protected void onDestroy(){generation++;handler.removeCallbacksAndMessages(null);discardRecording();network.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("history",history);save(out,"draft",drafts);save(out,"choice",choices);}
    private void save(Bundle out,String key,Map<String,String> values){Bundle b=new Bundle();for(Map.Entry<String,String> item:values.entrySet())b.putString(item.getKey(),item.getValue());out.putBundle(key,b);}
    private void restore(Bundle in,String key,Map<String,String> values){Bundle b=in.getBundle(key);if(b!=null)for(String k:b.keySet())values.put(k,b.getString(k,""));}
    private boolean editing(){return getCurrentFocus() instanceof EditText&&getCurrentFocus().hasFocus();}
    private void load(boolean manual){
        if(busy)return;
        AnkaiConnection connection=store.load();
        if(connection==null){dashboard=null;cards.removeAllViews();cards.addView(label("Verbinde deinen Assistant mit Ankaï, um Fragen und Vorschläge zu beantworten.",17,TEXT,false));cards.addView(button("Ankaï verbinden",true,()->startActivity(new Intent(this,MainActivity.class))));showStatus("Noch nicht verbunden");return;}
        execute(connection,client->{PersonalDashboard result=client.loadPersonalDashboard();return result;},value->{dashboard=(PersonalDashboard)value;showStatus("");if(manual||!editing())render();},manual?"Wird aktualisiert …":null);
    }
    private interface Call {Object run(AnkaiClient client)throws Exception;}
    private interface Success {void run(Object value);}
    private void execute(AnkaiConnection connection,Call call,Success success,String message){
        if(busy)return;busy=true;setControls(false);if(message!=null)showStatus(message);int own=++generation;
        network.execute(()->{
            AnkaiClient client=connection.newClient();Object value=null;Exception failure=null;
            try{value=call.run(client);}catch(Exception e){failure=e;}
            Object result=value;Exception error=failure;
            runOnUiThread(()->{
                if(isDestroyed()||own!=generation)return;
                AnkaiConnection current=store.load();
                if(current==null||!current.baseUrl.equals(connection.baseUrl)||!current.username.equals(connection.username)||!current.password.equals(connection.password)){
                    busy=false;dashboard=null;load(true);return;
                }
                store.saveSessionCookie(client.sessionCookie());busy=false;setControls(true);
                if(error==null)success.run(result);else showStatus(error.getMessage()==null?"Verbindung fehlgeschlagen. Bitte Status aktualisieren.":error.getMessage());
            });
        });
    }
    private void render(){
        if(dashboard==null)return;cards.removeAllViews();controls.clear();
        subtitle.setText(dashboard.openCount()+" offen · Fragen beantworten und nächste Schritte freigeben");
        openTab.setTextColor(history?MUTED:ACCENT);historyTab.setTextColor(history?ACCENT:MUTED);
        List<PersonalDecision> rows=new ArrayList<>();for(PersonalDecision d:dashboard.decisions)if(history?!d.isPending():d.isPending())rows.add(d);
        Collections.reverse(rows);rows.sort((a,b)->Boolean.compare(b.canRespond(),a.canRespond()));int n=0;for(PersonalDecision d:rows){if(++n>100)break;cards.addView(card(d));}
        if(rows.isEmpty()){cards.addView(label(history?"Hier erscheinen deine erledigten Entscheidungen.":"Alles durchgesehen",22,TEXT,true));cards.addView(label(history?"Ergebnisse bewerten hilft deinen Agenten, bessere Vorschläge zu machen.":"Deine Agenten bereiten die nächsten Schritte vor. Sobald du entscheiden kannst, erscheint hier eine Karte.",16,MUTED,false));}
        cards.addView(button("Ziele, Agenten & Konnektoren verwalten ↗",false,this::openWeb));setControls(!busy);
    }
    private View card(PersonalDecision d){
        LinearLayout card=column();card.setPadding(dp(18),dp(16),dp(18),dp(14));card.setBackground(shape(SURFACE,0xff394c40,18));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(16);card.setLayoutParams(cp);
        card.addView(label(d.kindLabel()+" · "+d.statusLabel(),12,ACCENT,true));card.addView(label(d.title,21,TEXT,true));
        card.addView(label(d.agentName+(d.goalTitle.isEmpty()?"":"  /  "+d.goalTitle),12,MUTED,false));
        card.addView(label(d.body,16,TEXT,false));
        if("action".equals(d.kind)){String details=details(d);if(!details.isEmpty())card.addView(label(details,14,MUTED,false));}
        LinearLayout scores=row();scores.setPadding(0,dp(8),0,dp(8));TextView risk=label("Risiko "+PersonalDecision.scoreLabel(d.risk)+" / 100",13,d.risk>=20?ACCENT:0xffb4dbac,true);TextView confidence=label("Konfidenz "+PersonalDecision.scoreLabel(d.confidence)+" %",13,MUTED,true);
        scores.addView(risk,new LinearLayout.LayoutParams(0,-2,1));scores.addView(confidence);card.addView(scores);
        if(d.external)card.addView(label("Externe Aktion · wird erst nach deiner Freigabe ausgeführt",13,ACCENT,true));
        if(!d.sourceTitles.isEmpty())card.addView(label("Quellen: "+String.join(" · ",d.sourceTitles),12,MUTED,false));
        if(d.expiresAt!=null&&!d.expiresAt.isEmpty())card.addView(label("Gültig bis "+d.expiresAt.replace('T',' ').replace(".000Z"," UTC"),12,MUTED,false));
        if(d.canRespond()){
            RadioGroup options=new RadioGroup(this);options.setOrientation(RadioGroup.VERTICAL);
            for(String option:d.options){RadioButton b=new RadioButton(this);b.setText(option);b.setTextColor(TEXT);b.setId(View.generateViewId());options.addView(b);controls.add(b);if(option.equals(choices.get(d.id)))b.setChecked(true);b.setOnCheckedChangeListener((v,checked)->{if(checked)choices.put(d.id,option);});}
            if(!d.options.isEmpty())card.addView(options);
            EditText input=new EditText(this);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setTextSize(15);input.setHint("Antwort oder Anpassung …");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMaxLines(5);input.setText(drafts.getOrDefault(d.id,""));
            input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){drafts.put(d.id,s.toString());}public void afterTextChanged(Editable e){}});card.addView(input);controls.add(input);
            LinearLayout actions=row();Button yes=button("action".equals(d.kind)?"Freigeben":"Antworten",true,()->respond(d,"approve"));Button no=button("Ablehnen",false,()->respond(d,"reject"));actions.addView(yes,new LinearLayout.LayoutParams(0,-2,1));actions.addView(no,new LinearLayout.LayoutParams(0,-2,1));card.addView(actions);
            LinearLayout feedback=row();feedback.addView(button("Anpassen",false,()->respond(d,"revise")),new LinearLayout.LayoutParams(0,-2,1));Button voice=button("Sprachfeedback",false,()->{});voice.setOnClickListener(v->record(d,voice));feedback.addView(voice,new LinearLayout.LayoutParams(0,-2,1));card.addView(feedback);
        }else if(!d.result.isEmpty())card.addView(label(d.result,14,TEXT,false));
        if(d.canEvaluate()){if(d.helpful!=null)card.addView(label(d.helpful?"Als hilfreich bewertet":"Als nicht hilfreich bewertet",13,MUTED,false));LinearLayout ratings=row();ratings.addView(button("Hilfreich",false,()->evaluate(d,true)),new LinearLayout.LayoutParams(0,-2,1));ratings.addView(button("Nicht hilfreich",false,()->evaluate(d,false)),new LinearLayout.LayoutParams(0,-2,1));card.addView(ratings);}
        return card;
    }
    private String details(PersonalDecision d){
        String[][] fields={{"provider","Anbieter"},{"recipient","Empfänger"},{"to","An"},{"subject","Betreff"},{"text","Nachricht"},{"title","Titel"},{"start","Beginn"},{"end","Ende"},{"due","Fällig"},{"location","Ort"},{"conditions","Bedingungen"},{"dataToSubmit","Zu übermittelnde Daten"},{"url","Website"},{"description","Beschreibung"},{"content","Wissen"},{"collectionUrl","Liste"},{"instruction","Auftrag"},{"hypothesis","Erwartete Verbesserung"},{"prompt","Neuer Agentenauftrag"}};
        List<String> lines=new ArrayList<>();for(String[] f:fields){Object v=d.payload.get(f[0]);if(v instanceof String&&!((String)v).isEmpty())lines.add(f[1]+": "+v);}
        Object model=d.payload.get("modelSelection");if(model instanceof Map){Map<?,?> m=(Map<?,?>)model;Object models=m.get("models");Object selected=models instanceof Map?((Map<?,?>)models).get(m.get("provider")):null;lines.add("Modell: "+(Boolean.TRUE.equals(m.get("auto"))?"Automatische Auswahl":String.valueOf(selected==null?m.get("provider"):selected))+" · Effort "+m.get("effort"));}
        return String.join("\n",lines);
    }
    private void respond(PersonalDecision d,String response){
        if(recorder!=null||busy)return;String message=drafts.getOrDefault(d.id,"").trim(),choice=choices.get(d.id);
        if("revise".equals(response)&&message.isEmpty()){showStatus("Beschreibe zuerst die gewünschte Anpassung oder nutze Sprachfeedback.");return;}
        if("approve".equals(response)&&!"action".equals(d.kind)&&!d.options.isEmpty()&&choice==null&&message.isEmpty()){showStatus("Wähle eine Antwort oder schreibe eine Nachricht.");return;}
        AnkaiConnection connection=store.load();if(connection==null){load(true);return;}
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(cards.getWindowToken(),0);cards.clearFocus();
        execute(connection,c->c.respondToPersonalDecision(d.id,d.revision,response,message,choice),result->{drafts.remove(d.id);choices.remove(d.id);showStatus("Gespeichert");load(true);},"Wird gesendet …");
    }
    private void evaluate(PersonalDecision d,boolean helpful){AnkaiConnection connection=store.load();if(connection==null)return;execute(connection,c->c.evaluatePersonalDecision(d.id,helpful),r->load(true),"Bewertung wird gespeichert …");}
    private void openWeb(){AnkaiConnection connection=store.load();if(connection!=null)startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(connection.baseUrl)));}
    private void record(PersonalDecision d,Button button){
        if(busy)return;if(recorder!=null){if(d.id.equals(recordingDecision.id))sendRecording();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},104);return;}
        try{recordingFile=File.createTempFile("decision-",".m4a",getCacheDir());recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(128000);recorder.setAudioSamplingRate(44100);recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.setMaxDuration(120000);recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)sendRecording();});recorder.prepare();recorder.start();recordingDecision=d;recordingButton=button;setControls(false);button.setEnabled(true);button.setText("Stopp & senden");showStatus("Aufnahme läuft · Sprachfeedback passt Aktionen an und erteilt keine Freigabe.");}
        catch(Exception e){discardRecording();showStatus("Aufnahme konnte nicht gestartet werden.");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==104){showStatus(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Mikrofon freigegeben. Tippe erneut auf Sprachfeedback.":"Für Sprachfeedback wird Mikrofonzugriff benötigt.");}}
    private void sendRecording(){
        if(recorder==null||recordingDecision==null)return;PersonalDecision d=recordingDecision;byte[] audio;
        try{recorder.stop();audio=Files.readAllBytes(recordingFile.toPath());}catch(Exception e){discardRecording();showStatus("Aufnahme zu kurz. Bitte erneut aufnehmen.");return;}
        discardRecording();AnkaiConnection connection=store.load();if(connection==null)return;
        execute(connection,c->c.sendPersonalDecisionVoice(d.id,d.revision,"feedback.m4a","audio/mp4",audio),r->{drafts.remove(d.id);choices.remove(d.id);load(true);},"Sprachfeedback wird verarbeitet …");
    }
    private void discardRecording(){if(recorder!=null){try{recorder.reset();}catch(RuntimeException ignored){}recorder.release();recorder=null;}if(recordingFile!=null){recordingFile.delete();recordingFile=null;}recordingDecision=null;if(recordingButton!=null)recordingButton.setText("Sprachfeedback");recordingButton=null;setControls(!busy);}
    private void showStatus(String text){status.setText(text);status.setVisibility(text==null||text.isEmpty()?View.GONE:View.VISIBLE);}
    private void setControls(boolean enabled){if(refresh!=null)refresh.setEnabled(enabled);if(openTab!=null)openTab.setEnabled(enabled&&recorder==null);if(historyTab!=null)historyTab.setEnabled(enabled&&recorder==null);for(View v:controls)v.setEnabled(enabled);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(android.view.Gravity.CENTER_VERTICAL);return v;}
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));v.setLineSpacing(dp(2),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private Button button(String title,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?BG:ACCENT}));
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),dp(6),dp(10),dp(6));
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33eaa77d),shape(primary?ACCENT:SURFACE,primary?ACCENT:0xff506255,10),null));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(3),dp(4),dp(3),dp(4));b.setLayoutParams(p);
        b.setOnClickListener(v->action.run());controls.add(b);return b;
    }
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
