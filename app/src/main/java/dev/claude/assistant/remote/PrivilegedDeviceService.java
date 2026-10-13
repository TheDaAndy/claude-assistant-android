package dev.claude.assistant.remote;

import android.os.Process;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import dev.claude.assistant.ankai.PrivilegedCommandPolicy;

/** Runs only inside Shizuku's app_process with its granted shell/root UID. */
public final class PrivilegedDeviceService extends IPrivilegedDeviceService.Stub {
    private volatile java.lang.Process active;
    private volatile int processGroup;
    public PrivilegedDeviceService() {}
    @Override public int uid(){return Process.myUid();}
    @Override public synchronized String execute(String command,int timeout) {
        ExecutorService drains=Executors.newFixedThreadPool(2);
        try {
            PrivilegedCommandPolicy.validate(command,timeout);
            // A separate session allows cancellation of foreground subprocesses as a group.
            java.lang.Process p=new ProcessBuilder("/system/bin/sh","-c","echo $$; exec /system/bin/setsid /system/bin/timeout --foreground -s KILL "+timeout+" /system/bin/sh -c \"$1\"","ankai",command).start();active=p;
            InputStream stdout=p.getInputStream();ByteArrayOutputStream first=new ByteArrayOutputStream();int ch;
            while(first.size()<32 && (ch=stdout.read())!=-1 && ch!='\n')first.write(ch);
            processGroup=Integer.parseInt(first.toString("UTF-8").trim());
            Future<Capture> out=drains.submit(()->capture(stdout));Future<Capture> err=drains.submit(()->capture(p.getErrorStream()));
            boolean finished=p.waitFor(timeout+1L,TimeUnit.SECONDS);
            boolean timedOut=!finished || (finished && (p.exitValue()==124 || p.exitValue()==137));
            if(timedOut)cancel();
            Capture o=out.get(3,TimeUnit.SECONDS),e=err.get(3,TimeUnit.SECONDS);
            return new JSONObject().put("status",finished && p.exitValue()==0?"ok":"error")
                    .put("data",new JSONObject().put("uid",uid()).put("exitCode",finished?p.exitValue():-1)
                    .put("timedOut",timedOut).put("stdout",o.text).put("stderr",e.text)
                    .put("truncated",o.truncated||e.truncated)).toString();
        }catch(Exception failure){
            cancel();return "{\"status\":\"error\",\"data\":\"Privilegierter Befehl fehlgeschlagen oder abgebrochen.\"}";
        }finally{active=null;processGroup=0;drains.shutdownNow();}
    }
    private static final class Capture {String text;boolean truncated;}
    private static Capture capture(InputStream in)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;boolean truncated=false;
        try(InputStream stream=in){while((count=stream.read(buffer))!=-1){int take=Math.min(count,2048-bytes.size());if(take>0)bytes.write(buffer,0,take);if(take<count)truncated=true;}}
        Capture result=new Capture();result.text=new String(bytes.toByteArray(),StandardCharsets.UTF_8).replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]","?");result.truncated=truncated;return result;
    }
    @Override public void cancel(){
        java.lang.Process p=active;if(p==null)return;
        int group=processGroup;
        if(group>1)try{new ProcessBuilder("/system/bin/kill","-KILL","--","-"+group).start().waitFor(2,TimeUnit.SECONDS);}catch(Exception ignored){}
        p.destroyForcibly();
    }
    @Override public void destroy(){cancel();System.exit(0);}
}
