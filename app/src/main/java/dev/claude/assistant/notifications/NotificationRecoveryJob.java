package dev.claude.assistant.notifications;

import android.app.job.*;
import java.util.concurrent.*;

/** Fallback sync even when Android declines a background foreground-service start. */
public final class NotificationRecoveryJob extends JobService {
    private ExecutorService worker;
    @Override public boolean onStartJob(JobParameters params) {
        ExecutorService ownWorker = Executors.newSingleThreadExecutor();
        worker = ownWorker;
        ownWorker.execute(() -> {
            try { NotificationSync.sync(this); } catch (Exception unavailable) { }
            finally { jobFinished(params, false); ownWorker.shutdown(); }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        if (worker != null) worker.shutdownNow();
        return NotificationSync.enabled(this);
    }
}
