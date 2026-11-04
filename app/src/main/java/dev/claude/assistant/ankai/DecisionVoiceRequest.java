package dev.claude.assistant.ankai;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Version-bound voice feedback; deliberately no chat/project routing fields. */
public final class DecisionVoiceRequest {
    private final String boundary="----ankaiDecision"+UUID.randomUUID().toString().replace("-","");
    private final byte[] body;
    public DecisionVoiceRequest(int revision,String filename,String type,byte[] audio){
        if(revision<1)throw new IllegalArgumentException("Vorschlagsversion fehlt");
        if(filename==null||!filename.matches("[A-Za-z0-9_.-]+")||type==null||!type.matches("audio/[A-Za-z0-9.+-]+")||audio==null||audio.length==0||audio.length>20*1024*1024)throw new IllegalArgumentException("Ungültige Sprachaufnahme");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"revision\"\r\n\r\n"+revision+"\r\n");
        write(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+type+"\r\n\r\n");
        out.write(audio,0,audio.length);write(out,"\r\n--"+boundary+"--\r\n");body=out.toByteArray();
    }
    private static void write(ByteArrayOutputStream out,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.write(bytes,0,bytes.length);}
    public String contentType(){return "multipart/form-data; boundary="+boundary;}
    public byte[] body(){return body.clone();}
}
