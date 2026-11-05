package dev.claude.assistant.ankai;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Map;

/** Immutable attachment identity from the exact proposal revision. */
public final class PersonalAttachment {
    public static final int MAX_BYTES=10*1024*1024;
    public final String id,filename,mimeType,sha256;
    public final int size;
    public PersonalAttachment(Map<String,Object> row){
        id=AnkaiJson.string(row,"id");filename=AnkaiJson.string(row,"filename");mimeType=AnkaiJson.string(row,"mimeType");sha256=AnkaiJson.string(row,"sha256");
        Object n=row.get("size");size=n instanceof Number?((Number)n).intValue():-1;
        if(id==null||!id.matches("[a-f0-9-]{36}")||filename==null||filename.isEmpty()||filename.length()>240||filename.matches("(?s).*[\\x00-\\x1f/\\\\].*")||sha256==null||!sha256.matches("[a-f0-9]{64}")||size<1||size>MAX_BYTES||!("image/jpeg".equals(mimeType)||"image/png".equals(mimeType)||"application/pdf".equals(mimeType)))throw new IllegalArgumentException("Ungültiger Anhang");
    }
    public void verify(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length!=size)throw new IOException("Anhang hat eine andere Größe. Bitte Vorschlag aktualisieren.");
        try{StringBuilder value=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format("%02x",b&255));
            if(!sha256.equals(value.toString()))throw new IOException("Anhang wurde verändert. Keine Freigabe erteilen.");
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException("Inhaltsprüfung nicht verfügbar.");}
    }
}
