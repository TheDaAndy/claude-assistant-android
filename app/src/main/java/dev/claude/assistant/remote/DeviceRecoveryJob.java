package dev.claude.assistant.remote;
import android.app.job.*;
public final class DeviceRecoveryJob extends JobService {
    @Override public boolean onStartJob(JobParameters p){DeviceRecovery.resume(this);return false;}
    @Override public boolean onStopJob(JobParameters p){return false;}
}
