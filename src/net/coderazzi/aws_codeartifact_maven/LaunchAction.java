package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
        final InputDialog dialog = new InputDialog();
        if (dialog.showAndGet()){
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> {
                        final TaskOutput to = launchTasks(dialog.getDomain(), dialog.getDomainOwner(),
                                dialog.getMavenServerId(), dialog.getMavenServerSettingsFile(),
                                dialog.getAWSPath());
                        SwingUtilities.invokeLater(()->{
                            if (showResults(project, to)){
                                showDialog(project);
                            }
                        });
                    }, "Generating credentials", true, project);
        }
    }

    private TaskOutput launchTasks(String domain, String domainOwner,
                                   String mavenServerId, String mavenSettingsFile, String awsPath){
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText("Checking settings file");
        SettingsUpdateTask settingsUpdateTask = new SettingsUpdateTask(mavenSettingsFile);
        TaskOutput taskOutput = settingsUpdateTask.locateServer(mavenServerId);
        if (taskOutput.ok) {
            progressIndicator.setText("Obtaining AWS credentials");
            taskOutput = AWSTask.getCredentials(domain, domainOwner, awsPath);
            if (taskOutput.ok) {
                progressIndicator.setText("Updating settings file");
                String credentials = taskOutput.output;
                taskOutput = settingsUpdateTask.setPassword(credentials);
            }
        }
        return taskOutput;
    }

    /**
     * Shows the results of the operation.
     * @return True if we need to show the main dialog again
     */
    private boolean showResults(Project project, TaskOutput output){
        if (output.ok) {
            Messages.showMessageDialog(project, "Credentials updated", "CodeArtifact + Maven",
                    Messages.getInformationIcon());
        } else {
            Messages.showErrorDialog(project, output.output, "CodeArtifact + Maven");
        }
        return !output.ok;
    }

}