package dev.claude.assistant.remote;

import android.content.Context;
import dev.claude.assistant.ankai.SecretStore;
import dev.claude.assistant.storage.EncryptedPrefsSecretStore;
import org.json.JSONObject;

public final class DeviceLinkStore {
    private final SecretStore store;
    public DeviceLinkStore(Context context) { store = new EncryptedPrefsSecretStore(context); }
    public String id() { return store.get("device.id"); }
    public String token() { return store.get("device.token"); }
    public String baseUrl() { return store.get("device.baseUrl"); }
    public String scopeLabel() { String label=store.get("device.scopeLabel");return label!=null?label:store.get("device.projectName"); }
    public long scopeRevision() { try{return Long.parseLong(store.get("device.scopeRevision"));}catch(Exception ignored){return 0;} }
    public void updateDevice(JSONObject device) throws Exception {
        long previous=scopeRevision();
        JSONObject scope=device.optJSONObject("scope");if(scope!=null)store.put("device.scope",scope.toString());
        if(device.has("scopeLabel")&&!device.isNull("scopeLabel"))store.put("device.scopeLabel",device.getString("scopeLabel"));
        store.put("device.scopeRevision",String.valueOf(device.optLong("scopeRevision",0)));
        if(previous!=scopeRevision())store.remove("device.results");
    }
    private static final Object CONTROL_LOCK=new Object();
    public long nextControlSequence(){synchronized(CONTROL_LOCK){long previous=0;try{previous=Long.parseLong(store.get("device.controlSequence"));}catch(Exception ignored){}long next=Math.max(System.currentTimeMillis(),previous+1);store.put("device.controlSequence",String.valueOf(next));return next;}}
    public boolean maximumRequested(){return "true".equals(store.get("device.maximum"));}
    public boolean maximumGranted(int uid){return maximumRequested() && String.valueOf(uid).equals(store.get("device.maximumUid"));}
    public void maximum(boolean value,int uid){store.put("device.maximum",String.valueOf(value));if(value)store.put("device.maximumUid",String.valueOf(uid));else store.remove("device.maximumUid");}
    public boolean enabled() { return "true".equals(store.get("device.enabled")); }
    public void enabled(boolean value) { store.put("device.enabled", String.valueOf(value)); }
    public void save(String id, String token, String url, String project) {
        clear(); store.put("device.token", token); store.put("device.baseUrl", url);
        store.put("device.projectName", project); store.put("device.id", id);
    }
    public String status() { String value = store.get("device.status"); return value == null ? "Pausiert" : value; }
    public void status(String value) { store.put("device.status", value); }
    public JSONObject cachedResult(String id) {
        try { return new JSONObject(store.get("device.results")).optJSONObject(id); }
        catch (Exception absent) { return null; }
    }
    public void cacheResult(String id, JSONObject result) throws Exception {
        JSONObject results;
        try { results = new JSONObject(store.get("device.results")); } catch (Exception absent) { results = new JSONObject(); }
        // Keep the recent command results, including confirmation receipts, across process restarts.
        if (results.length() >= 100) results = new JSONObject();
        results.put(id, result); store.put("device.results", results.toString());
    }
    public void clear() {
        for (String key : new String[]{"id", "token", "baseUrl", "projectName", "enabled", "status", "results", "scope", "scopeLabel", "scopeRevision", "maximum", "maximumUid", "controlSequence"}) store.remove("device." + key);
    }
}
