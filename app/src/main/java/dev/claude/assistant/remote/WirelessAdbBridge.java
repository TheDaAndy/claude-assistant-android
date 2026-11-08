package dev.claude.assistant.remote;

import android.content.Context;
import io.github.muntashirakon.adb.AdbStream;
import dev.claude.assistant.ankai.PrivilegedCommandPolicy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Executes shell-v2 over the locally paired TLS ADB transport; no separate privileged app. */
public final class WirelessAdbBridge {
    private final Context context;
    private volatile boolean closed;
    private volatile AdbStream active;
    public WirelessAdbBridge(Context context){this.context=context.getApplicationContext();}
    public static boolean running(){WirelessAdbConnection c=WirelessAdbConnection.current();return c!=null&&c.isConnected();}
    public static boolean authorized(){WirelessAdbConnection c=WirelessAdbConnection.current();return c!=null&&c.verified();}
    public static int privilegeUid(){WirelessAdbConnection c=WirelessAdbConnection.current();return c==null?-1:c.uid();}
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    public synchronized JSONObject execute(String command,int timeout)throws Exception {
        PrivilegedCommandPolicy.validate(command,timeout);
        if(closed||!authorized())throw new IOException("Wireless Debugging nicht verbunden.");
        // A remote timeout survives a broken transport. No command is retried.
        AdbStream stream=WirelessAdbConnection.get(context).openStream("shell,v2,raw:exec /system/bin/timeout -s KILL "+timeout+" /system/bin/sh -c "+quote(command));
        active=stream;ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        timer.schedule(()->{try{stream.close();}catch(IOException ignored){}},timeout+3L,TimeUnit.SECONDS);
        try(InputStream in=new BufferedInputStream(stream.openInputStream())){
            dev.claude.assistant.ankai.AdbShellResult result=dev.claude.assistant.ankai.AdbShellResult.read(in);
            if(closed)throw new IOException("Ausführung unterbrochen; Ergebnis unbekannt.");
            int exit=result.exitCode;
            return new JSONObject().put("status",exit==0?"ok":"error").put("data",new JSONObject().put("uid",privilegeUid()).put("exitCode",exit)
                .put("timedOut",exit==124||exit==137).put("stdout",result.stdout).put("stderr",result.stderr).put("truncated",result.truncated));
        }finally{active=null;timer.shutdownNow();stream.close();DevicePresenceMonitor.changed();}
    }
    public void close(){closed=true;AdbStream s=active;if(s!=null)try{s.close();}catch(IOException ignored){} }
}
