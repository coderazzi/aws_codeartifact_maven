package net.coderazzi.aws_codeartifact_maven.internal;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.concurrent.ScheduledFuture;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;

public final class SchedulerService {
    private static final SchedulerService instance = new SchedulerService();
    private ScheduledFuture<?> scheduledFuture;

    private SchedulerService() {
        // singleton
    }

    public static SchedulerService getInstance() {
        return instance;
    }

    public void updateSchedule(long intervalMinutes) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        if (intervalMinutes == 0) {
            return;
        }

        scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
            var output = CredentialsUpdater.runCredentialUpdateTask(AppSettings.getInstance().getState());
            NotificationUtil.renderOutput(output);
        }, intervalMinutes, intervalMinutes, MINUTES);
    }
}
