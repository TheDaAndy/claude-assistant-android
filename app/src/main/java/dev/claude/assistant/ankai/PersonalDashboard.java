package dev.claude.assistant.ankai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PersonalDashboard {
    public final List<PersonalDecision> decisions;
    private PersonalDashboard(List<PersonalDecision> decisions){this.decisions=Collections.unmodifiableList(decisions);}
    @SuppressWarnings("unchecked")
    public static PersonalDashboard from(Map<String,Object> json){
        Map<String,String> agents=names(AnkaiJson.list(json,"agents"),"name"),goals=names(AnkaiJson.list(json,"goals"),"title"),knowledge=names(AnkaiJson.list(json,"knowledge"),"title");
        List<PersonalDecision> rows=new ArrayList<>();
        for(Object item:AnkaiJson.list(json,"decisions")){
            if(!(item instanceof Map))continue;
            Map<String,Object> row=(Map<String,Object>)item;
            String id=AnkaiJson.string(row,"id");if(id==null||id.trim().isEmpty())continue;
            List<String> sources=new ArrayList<>();
            for(Object source:AnkaiJson.list(row,"sourceIds")){String title=knowledge.get(String.valueOf(source));if(title!=null)sources.add(title);}
            rows.add(new PersonalDecision(row,agents.get(AnkaiJson.string(row,"agentId")),goals.get(AnkaiJson.string(row,"goalId")),sources));
        }
        return new PersonalDashboard(rows);
    }
    @SuppressWarnings("unchecked") private static Map<String,String> names(List<Object> rows,String label){
        Map<String,String> result=new LinkedHashMap<>();for(Object item:rows)if(item instanceof Map){Map<String,Object> row=(Map<String,Object>)item;String id=AnkaiJson.string(row,"id");if(id!=null)result.put(id,AnkaiJson.string(row,label));}return result;
    }
    public int openCount(){int count=0;for(PersonalDecision row:decisions)if(row.canRespond())count++;return count;}
}
