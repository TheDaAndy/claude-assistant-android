package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

/** An inbox snapshot. Execution policy remains exclusively on the server. */
public final class PersonalDecision {
    private static final List<String> INTERNAL = Arrays.asList("memory_note","nextcloud_task","calendar_event","agent_trial");
    public final String id, kind, status, title, body, agentName, goalTitle, result, expiresAt;
    public final int revision;
    public final double risk, confidence;
    public final boolean external;
    public final List<String> options, sourceTitles;
    public final Map<String,Object> payload;
    public final String actionType;
    public final List<PersonalAttachment> attachments;
    private final boolean attachmentsValid;
    public final Boolean helpful;

    PersonalDecision(Map<String,Object> row, String agentName, String goalTitle, List<String> sources) {
        id=AnkaiJson.string(row,"id");kind=value(row,"kind","");status=value(row,"status","unknown");
        title=value(row,"title","Entscheidung");body=value(row,"body","");
        this.agentName=agentName==null?"Agent":agentName;this.goalTitle=goalTitle==null?"":goalTitle;
        Object version=row.get("revision");
        revision=version instanceof Number && ((Number)version).doubleValue()==((Number)version).intValue()?((Number)version).intValue():0;
        risk=number(row,"risk");confidence=number(row,"confidence");
        Map<String,Object> action=AnkaiJson.object(row,"action");
        actionType=value(action,"type","");
        Map<String,Object> data=AnkaiJson.object(action,"payload");payload=data==null?Collections.emptyMap():Collections.unmodifiableMap(data);
        List<PersonalAttachment> files=new ArrayList<>();boolean valid=true;int total=0;
        for(Object item:AnkaiJson.list(payload,"attachments")){
            try{if(!(item instanceof Map))throw new IllegalArgumentException();
                @SuppressWarnings("unchecked") Map<String,Object> file=(Map<String,Object>)item;
                PersonalAttachment attachment=new PersonalAttachment(file);files.add(attachment);total+=attachment.size;
            }catch(IllegalArgumentException error){valid=false;}
        }
        attachments=Collections.unmodifiableList(files);attachmentsValid=valid&&files.size()<=8&&total<=20*1024*1024;
        external="action".equals(kind) && (Boolean.TRUE.equals(row.get("external")) || !INTERNAL.contains(actionType)
                || !AnkaiJson.list(payload,"attendees").isEmpty() || Boolean.TRUE.equals(payload.get("sendInvitations")));
        List<String> choices=new ArrayList<>();
        for(Object item:AnkaiJson.list(row,"options"))if(item instanceof String)choices.add((String)item);
        options=Collections.unmodifiableList(choices);sourceTitles=Collections.unmodifiableList(new ArrayList<>(sources));
        Map<String,Object> outcome=AnkaiJson.object(row,"outcome");
        helpful=outcome!=null && outcome.get("helpful") instanceof Boolean?(Boolean)outcome.get("helpful"):null;
        Map<String,Object> evidence=AnkaiJson.object(row,"result");
        String evidenceText=AnkaiJson.string(evidence,"evidence");
        List<Object> feedback=AnkaiJson.list(row,"feedback");
        if((evidenceText==null||evidenceText.isEmpty())&&!feedback.isEmpty()&&feedback.get(feedback.size()-1) instanceof Map){
            @SuppressWarnings("unchecked") Map<String,Object> last=(Map<String,Object>)feedback.get(feedback.size()-1);evidenceText=AnkaiJson.string(last,"message");
        }
        result=evidenceText==null?"":evidenceText;expiresAt=AnkaiJson.string(row,"expiresAt");
    }
    public boolean canRespond(){return attachmentsValid&&"proposed".equals(status)&&revision>0&&validScore(risk)&&validScore(confidence)
            &&("action".equals(kind)||"question".equals(kind)||"strategy".equals(kind));}
    public boolean isPending(){return Arrays.asList("proposed","needs_revision","approved","running","unknown").contains(status);}
    public boolean canEvaluate(){return Arrays.asList("done","failed","unknown","answered","rejected").contains(status);}
    public String statusLabel(){
        switch(status){case "proposed":return "Deine Entscheidung";case "approved":return "Freigegeben";case "running":return "Wird ausgeführt";
            case "done":return "Erledigt";case "failed":return "Fehlgeschlagen";case "answered":return "Beantwortet";case "rejected":return "Abgelehnt";
            case "needs_revision":return "Wird überarbeitet";default:return "Ergebnis unbekannt";}
    }
    public String kindLabel(){return "action".equals(kind)?"Aktion":"strategy".equals(kind)?"Strategische Frage":"Wissensfrage";}
    public static String scoreLabel(double value){if(!validScore(value))return "—";return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static boolean validScore(double v){return Double.isFinite(v)&&v>=0&&v<=100;}
    private static double number(Map<String,Object> row,String key){Object value=row.get(key);return value instanceof Number?((Number)value).doubleValue():Double.NaN;}
    private static String value(Map<String,Object> row,String key,String fallback){String value=AnkaiJson.string(row,key);return value==null?fallback:value;}
    @Override public String toString(){return "PersonalDecision{"+id+", revision="+revision+", status="+status+"}";}
}
