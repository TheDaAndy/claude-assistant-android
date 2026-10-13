package dev.claude.assistant.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import dev.claude.assistant.ankai.AnkaiConnection;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;
import org.json.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;

/** Device grant editor, deliberately independent from the voice default project. */
public final class DeviceScopeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DeviceHttp http = new DeviceHttp();
    private DeviceLinkStore store;
    private AnkaiConnection connection;
    private RadioGroup modes;
    private Button projectsButton, chatButton, save;
    private TextView feedback;
    private final Map<String,String> projects = new LinkedHashMap<>();
    private String chatId, chatName, projectDescription;
    private boolean editing, busy;
    private final String[] types = {"global", "user", "projects", "chat"};
    private String type() { int id = modes.getCheckedRadioButtonId(); return id >= 2100 && id < 2104 ? types[id - 2100] : "user"; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(0,dp(8),0,dp(8)); return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new DeviceLinkStore(this);
        connection = EncryptedPrefsSecretStore.connectionStore(this).load();
        if (connection == null) { finish(); return; }
        editing = store.id() != null;
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(26,26,26));
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(16),dp(16),dp(16),dp(24)); scroll.addView(layout);
        layout.addView(text(editing ? "Gerätefreigabe ändern" : "Gerät mit Ankaï koppeln",24));
        layout.addView(text("Wähle den Zugriff wie bei Secrets. Das Standardprojekt für Sprachaufträge bleibt unabhängig davon.",16));
        modes = new RadioGroup(this); modes.setOrientation(RadioGroup.VERTICAL);
        String[] labels = {"Global\nAlle Nutzer, alle Chats, Prompts und Automationen", "Mein Nutzer\nAlle meine Prompts und Automationen, auch ohne Projekt", "Ausgewählte Projekte\nEin oder mehrere Projekte und deren berechtigte Nutzer", "Ein bestimmter Chat\nNur dieser Chat und seine weiteren Runs"};
        for (int i=0;i<labels.length;i++) {
            RadioButton b = new RadioButton(this); b.setId(2100+i); b.setText(labels[i]); b.setTextColor(Color.WHITE); b.setPadding(0,dp(10),0,dp(10)); modes.addView(b);
        }
        layout.addView(modes); modes.check(2101);
        projectsButton = new Button(this); projectsButton.setText("Projekte auswählen"); projectsButton.setOnClickListener(v -> chooseReferences(true)); layout.addView(projectsButton);
        chatButton = new Button(this); chatButton.setText("Chat auswählen"); chatButton.setOnClickListener(v -> chooseReferences(false)); layout.addView(chatButton);
        feedback = text("",14); feedback.setTextColor(Color.rgb(255,191,150)); layout.addView(feedback);
        layout.addView(text("Nur du verwaltest diese Kopplung. Der Token bleibt verschlüsselt. Einstellungsseiten benötigen weiterhin deine Bestätigung am Handy.",14));
        save = new Button(this); save.setText(editing ? "Freigabe speichern" : "Gerät koppeln"); save.setOnClickListener(v -> confirm()); layout.addView(save);
        Button cancel = new Button(this); cancel.setText("Abbrechen"); cancel.setOnClickListener(v -> finish()); layout.addView(cancel);
        modes.setOnCheckedChangeListener((g,id) -> render()); setContentView(scroll);
        if (state != null) {
            try { applyScope(new JSONObject(state.getString("draft"))); } catch (Exception ignored) { }
        } else if (editing) {
            busy = true; render();
            worker.execute(() -> {
                try {
                    JSONArray devices = http.account(connection,"/api/devices","GET",null).getJSONArray("devices"); JSONObject device = null;
                    for(int i=0;i<devices.length();i++) if(store.id().equals(devices.getJSONObject(i).optString("id"))) device = devices.getJSONObject(i);
                    if(device == null) throw new IllegalStateException("Kopplung nicht gefunden; zuerst widerrufen oder neu koppeln.");
                    store.updateDevice(device); final JSONObject d = device;
                    runOnUiThread(() -> { if(isDestroyed())return; busy=false; applyScope(d.optJSONObject("scope")); render(); });
                } catch(Exception error) { runOnUiThread(() -> { if(!isDestroyed()) { feedback.setText("Freigabe konnte nicht geladen werden. Bitte später erneut versuchen."); save.setEnabled(false); } }); }
            });
        }
        render();
    }
    private void applyScope(JSONObject scope) {
        if(scope == null) return;
        String t = scope.optString("type","user");
        for(int i=0;i<types.length;i++) if(types[i].equals(t)) modes.check(2100+i);
        projects.clear(); JSONArray ids = scope.optJSONArray("projectIds");
        if(ids != null) for(int i=0;i<ids.length();i++) projects.put(ids.optString(i),ids.optString(i));
        chatId = scope.optString("sessionId",null); chatName = chatId;
        projectDescription="projects".equals(t)?store.scopeLabel():null;
        if(chatId!=null && store.scopeLabel()!=null && store.scopeLabel().startsWith("Chat: "))chatName=store.scopeLabel().substring(6);
        if(scope.optBoolean("ownerOnly")) feedback.setText("Bisher: nur dein Nutzer in diesem Projekt. Speichern bestätigt ausdrücklich die neu gewählte Freigabe.");
        render();
    }
    private void render() {
        if(save==null)return;
        projectsButton.setVisibility("projects".equals(type()) ? View.VISIBLE : View.GONE);
        chatButton.setVisibility("chat".equals(type()) ? View.VISIBLE : View.GONE);
        projectsButton.setText(projects.isEmpty() ? "Projekte auswählen" : "Projekte ("+projects.size()+"): "+(projectDescription!=null?projectDescription:String.join(", ",projects.values())));
        chatButton.setText(chatId==null ? "Chat auswählen" : "Chat: "+chatName);
        for(int i=0;i<modes.getChildCount();i++) modes.getChildAt(i).setEnabled(!busy);
        projectsButton.setEnabled(!busy); chatButton.setEnabled(!busy); save.setEnabled(!busy);
    }
    private JSONObject scope() throws Exception {
        JSONObject s = new JSONObject().put("type",type());
        if("projects".equals(type())) {
            if(projects.isEmpty() || projects.size()>50)throw new IllegalArgumentException("Bitte ein bis 50 Projekte auswählen.");
            s.put("projectIds",new JSONArray(projects.keySet()));
        } else if("chat".equals(type())) {
            if(chatId==null)throw new IllegalArgumentException("Bitte einen Chat auswählen."); s.put("sessionId",chatId);
        }
        return s;
    }
    private String description() {
        if("global".equals(type())) return "GLOBAL: Alle Nutzer dieser Ankaï-Instanz dürfen das Handy aus allen Chats, Prompts und Automationen ansprechen.";
        if("user".equals(type())) return "NUTZER: Alle Prompts und Automationen unter deinem angemeldeten Nutzer dürfen das Handy ansprechen, auch ohne Projekt.";
        if("projects".equals(type())) return "PROJEKTE: "+String.join(", ",projects.values())+"\nBerechtigte Nutzer dieser Projekte dürfen das Handy ansprechen.";
        return "CHAT: "+chatName+"\nDie Freigabe gilt für diesen Chat und seine weiteren Runs.";
    }
    private void confirm() {
        final JSONObject scope;
        try { scope=scope(); } catch(Exception e) {feedback.setText(e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle(editing ? "Freigabe ändern?" : "Gerätezugriff freigeben?")
                .setMessage(description()+"\n\nGeräteinfos und lesbare Einstellungen können abgerufen werden. Einstellungsseiten öffnest du erst nach Bestätigung am Handy."+(editing?"\nDer Kanal wird pausiert; offene Aktionen der alten Freigabe werden verworfen.":""))
                .setNegativeButton("Abbrechen",null).setPositiveButton(editing?"Speichern":"Koppeln",(d,w)->submit(scope)).show();
    }
    private void submit(JSONObject scope) {
        busy=true;feedback.setText("Speichern …");render();
        // Pause before changing grant; do not silently reactivate the new permission.
        if(editing) {store.enabled(false);stopService(new Intent(this,DeviceChannelService.class));}
        worker.execute(() -> {
            try {
                JSONObject body=new JSONObject().put("scope",scope); JSONObject response;
                if(editing) response=http.account(connection,"/api/devices/"+store.id()+"/scope","PATCH",body);
                else {
                    body.put("name",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
                    response=http.account(connection,"/api/devices","POST",body);
                    store.save(response.getJSONObject("device").getString("id"),response.getString("token"),connection.baseUrl,response.getJSONObject("device").optString("scopeLabel"));
                }
                store.updateDevice(response.getJSONObject("device"));store.status("Pausiert · Gerätezugriff manuell aktivieren");
                runOnUiThread(()->{if(!isDestroyed()){setResult(RESULT_OK);finish();}});
            }catch(Exception error){runOnUiThread(()->{if(!isDestroyed()){busy=false;feedback.setText("Speichern fehlgeschlagen: "+error.getMessage()+". Der Server benötigt die erweiterten Gerätefreigaben.");render();}});}
        });
    }
    private void chooseReferences(boolean multi) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),0,dp(16),0);
        EditText query=new EditText(this);query.setHint(multi?"Projektname oder ID suchen":"Chattitel oder ID suchen");query.setSingleLine(true);content.addView(query);
        Button search=new Button(this);search.setText("Suchen");content.addView(search);
        TextView hint=new TextView(this);content.addView(hint);
        ListView list=new ListView(this);list.setChoiceMode(multi?ListView.CHOICE_MODE_MULTIPLE:ListView.CHOICE_MODE_SINGLE);
        content.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
        Map<String,String> chosen=new LinkedHashMap<>(projects);String[] chosenChat={chatId,chatName};
        List<String> ids=new ArrayList<>(),labels=new ArrayList<>();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(multi?"Projekte freigeben":"Chat freigeben").setView(content)
                .setNegativeButton("Abbrechen",null).setPositiveButton("Übernehmen",(d,w)->{
                    if(multi){projects.clear();projects.putAll(chosen);projectDescription=null;}else{chatId=chosenChat[0];chatName=chosenChat[1];}render();
                }).create();
        list.setOnItemClickListener((parent,v,pos,id)->{
            if(multi){if(list.isItemChecked(pos))chosen.put(ids.get(pos),labels.get(pos).split("\n",2)[0]);else chosen.remove(ids.get(pos));}
            else{chosenChat[0]=ids.get(pos);chosenChat[1]=labels.get(pos).split("\n",2)[0];}
        });
        Runnable load=()->{
            search.setEnabled(false);hint.setText("Laden …");list.setEnabled(false);
            String q=query.getText().toString();
            worker.execute(()->{
                try{
                    JSONObject response=http.account(connection,"/api/devices/scope-options?kind="+(multi?"projects":"chats")+"&q="+URLEncoder.encode(q,"UTF-8"),"GET",null);
                    JSONArray options=response.getJSONArray("options");
                    runOnUiThread(()->{
                        if(isDestroyed()||!dialog.isShowing())return;
                        ids.clear();labels.clear();
                        for(int i=0;i<options.length();i++){
                            JSONObject item=options.optJSONObject(i);if(item==null)continue;
                            ids.add(item.optString("id"));labels.add(item.optString("label")+"\n"+item.optString("id"));
                        }
                        list.setAdapter(new ArrayAdapter<>(this,multi?android.R.layout.simple_list_item_multiple_choice:android.R.layout.simple_list_item_single_choice,labels));
                        for(int i=0;i<ids.size();i++){
                            list.setItemChecked(i,multi?chosen.containsKey(ids.get(i)):ids.get(i).equals(chosenChat[0]));
                            if(multi && chosen.containsKey(ids.get(i)))chosen.put(ids.get(i),options.optJSONObject(i).optString("label"));
                        }
                        search.setEnabled(true);list.setEnabled(true);
                        hint.setText(response.optBoolean("hasMore")?"Weitere Treffer vorhanden · Suche eingrenzen":ids.isEmpty()?"Keine passenden Einträge":"Auswahl unabhängig vom Sprach-Standardprojekt");
                    });
                }catch(Exception error){runOnUiThread(()->{if(!isDestroyed()&&dialog.isShowing()){search.setEnabled(true);hint.setText("Auswahl konnte nicht geladen werden: "+error.getMessage());}});}
            });
        };
        search.setOnClickListener(v->load.run());dialog.show();load.run();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);try{state.putString("draft",scope().toString());}catch(Exception ignored){}
    }
    @Override public void onDestroy(){http.cancel();worker.shutdownNow();super.onDestroy();}
}
