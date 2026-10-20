package dev.claude.assistant.remote;

import android.app.job.*;
import android.content.*;
import android.os.UserManager;

/** System-managed recovery only while the user has left the device channel enabled. */
public final class DeviceRecovery {
    static final int JOB = 2802;
    public static void configure(Context context, boolean enabled) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(!enabled){scheduler.cancel(JOB);return;}
        if(scheduler.getPendingJob(JOB)!=null)return;
        scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,DeviceRecoveryJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    public static void resume(Context context) {
        if(!context.getSystemService(UserManager.class).isUserUnlocked())return;
        DeviceLinkStore store=new DeviceLinkStore(context);
        if(!store.enabled() || store.id()==null){configure(context,false);return;}
        configure(context,true);
        try{context.startForegroundService(new Intent(context,DeviceChannelService.class));}
        catch(RuntimeException blocked){store.status("Wiederaufnahme von Android blockiert · App öffnen / Akku-Freigabe prüfen");}
    }
}
