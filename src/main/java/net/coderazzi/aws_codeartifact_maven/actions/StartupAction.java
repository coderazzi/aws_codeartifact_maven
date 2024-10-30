package net.coderazzi.aws_codeartifact_maven.actions;

import static java.util.Objects.requireNonNull;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import net.coderazzi.aws_codeartifact_maven.internal.CredentialsUpdater;
import net.coderazzi.aws_codeartifact_maven.internal.NotificationUtil;
import net.coderazzi.aws_codeartifact_maven.internal.SchedulerService;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;
import org.jetbrains.annotations.NotNull;

public class StartupAction implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        var settings = AppSettings.getInstance();
        var state = requireNonNull(settings.getState());

        if (state.getRefreshIntervalMinutes() == 0) {
            return;
        }

        var output = CredentialsUpdater.runCredentialUpdateTask(AppSettings.getInstance().getState());
        NotificationUtil.renderOutput(output);
        SchedulerService.getInstance().updateSchedule(state.getRefreshIntervalMinutes());
    }
}
