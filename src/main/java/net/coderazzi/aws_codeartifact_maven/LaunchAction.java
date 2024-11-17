package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import net.coderazzi.aws_codeartifact_maven.gui.MainDialog;
import org.jetbrains.annotations.NotNull;

public class LaunchAction extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        showDialog(e.getProject());
    }

    private void showDialog(Project project) {
        new MainDialog(project).show();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}