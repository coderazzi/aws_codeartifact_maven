package net.coderazzi.aws_codeartifact_maven.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import net.coderazzi.aws_codeartifact_maven.internal.CredentialsUpdater;
import net.coderazzi.aws_codeartifact_maven.internal.NotificationUtil;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;
import org.jetbrains.annotations.NotNull;

public class TriggerAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var output = CredentialsUpdater.runCredentialUpdateTask(AppSettings.getInstance().getState());
        NotificationUtil.renderOutput(output);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
