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
                        final InputDialogState state = dialog.getState();
                        final OperationOutput to = launchTasks(state.getDomain(), state.getDomainOwner(),
                                state.getMavenServerId(), state.getMavenServerSettingsFile(),
                                state.getAWSPath(), state.getProfile(), state.getRegion());
                        if (to!=null ) {
                            SwingUtilities.invokeLater(() -> {
                                if (showResults(project, to)) {
                                    showDialog(project);
                                }
                            });
                        }
                    }, "Generating Credentials", true, project);
        }
    }

    private OperationOutput launchTasks(String domain, String domainOwner,
                                        String mavenServerId, String mavenSettingsFile,
                                        String awsPath, String awsProfile, String awsRegion){
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText("Checking settings file");
        MavenSettingsFileHandler mavenSettingsFileHandler = new MavenSettingsFileHandler(mavenSettingsFile);
        OperationOutput taskOutput = mavenSettingsFileHandler.locateServer(mavenServerId);
        if (taskOutput.ok && !progressIndicator.isCanceled()) {
            progressIndicator.setText("Obtaining AWS credentials");
            taskOutput = AWSInvoker.getCredentials(domain, domainOwner, awsPath, awsProfile, awsRegion,
                    progressIndicator::isCanceled);
            if (taskOutput!=null && taskOutput.ok  && !progressIndicator.isCanceled()) {
                progressIndicator.setText("Updating settings file");
                String credentials = taskOutput.output;
                taskOutput = mavenSettingsFileHandler.setPassword(credentials);
            }
        }
        return progressIndicator.isCanceled()? null : taskOutput;
    }

    /**
     * Shows the results of the operation.
     * @return True if we need to show the main dialog again
     */
    private boolean showResults(Project project, OperationOutput output){
        if (output.ok) {
            Messages.showMessageDialog(project, "Credentials updated", InputDialog.COMPONENT_TITLE,
                    Messages.getInformationIcon());
        } else {
            Messages.showErrorDialog(project, output.output, InputDialog.COMPONENT_TITLE);
        }
        return !output.ok;
    }

}