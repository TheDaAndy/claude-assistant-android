package dev.claude.assistant.ankai;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded shell-v2 decoder. Missing exit status is an uncertain execution, never success. */
public final class AdbShellResult {
    public final String stdout,stderr;
    public final int exitCode;
    public final boolean truncated;
    private AdbShellResult(ByteArrayOutputStream out,ByteArrayOutputStream err,int exit,boolean cut){
        stdout=clean(out);stderr=clean(err);exitCode=exit;truncated=cut;
    }
    public static AdbShellResult read(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();boolean cut=false;
        for(;;){
            int id=in.read();if(id<0)throw new EOFException("ADB exit status missing");
            long length=0;for(int i=0;i<4;i++){int b=in.read();if(b<0)throw new EOFException();length|=(long)b<<(i*8);}
            if(length>1024*1024)throw new IOException("Invalid ADB frame size");
            if(id==3){if(length!=1)throw new IOException("Invalid ADB exit status");int exit=in.read();if(exit<0)throw new EOFException();return new AdbShellResult(out,err,exit,cut);}
            if(id!=1&&id!=2)throw new IOException("Unexpected ADB frame");
            ByteArrayOutputStream target=id==1?out:err;byte[] buffer=new byte[2048];
            while(length>0){int count=in.read(buffer,0,(int)Math.min(length,buffer.length));if(count<0)throw new EOFException();length-=count;
                int take=Math.min(count,2048-target.size());target.write(buffer,0,take);cut|=take<count;}
        }
    }
    private static String clean(ByteArrayOutputStream b){return new String(b.toByteArray(),StandardCharsets.UTF_8).replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]","?");}
}
