package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import net.coderazzi.aws_codeartifact_maven.utils.AWSInvoker;
import net.coderazzi.aws_codeartifact_maven.utils.MavenSettingsFileHandler;
import net.coderazzi.aws_codeartifact_maven.utils.OperationOutput;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

@SuppressWarnings({"unchecked"})
class GenerationDialog extends DialogWrapper {


    public GenerationDialog(final Project project,
                            final Configuration state,
                            final boolean usePresentConfigurationOnly) {
        super(true); // use current window as parent
        getWindow().addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                launch(project, state, usePresentConfigurationOnly);
            }
        });
    }

    private void launch(final Project project,
                        final Configuration state,
                        final boolean usePresentConfigurationOnlye){
        ProgressManager.getInstance().runProcessWithProgressSynchronously( () -> {
            final OperationOutput to = launchTasks(
                    state.getCurrentConfiguration().domain,
                    state.getCurrentConfiguration().domainOwner,
                    state.getCurrentConfiguration().mavenServerId,
                    state.getMavenServerSettingsFile(),
                    state.getAWSPath(),
                    state.getCurrentConfiguration().profile,
                    state.getCurrentConfiguration().region);
//            if (to != null) {
//                ApplicationManager.getApplication().invokeLater(() -> {
//                    if (showResults(project, to)) {
//                        showDialog(project);
//                    }
//                });
//            }
        }, "Generating Auth Token", true, project);
    }

    private OperationOutput launchTasks(String domain, String domainOwner,
                                        String mavenServerId, String mavenSettingsFile,
                                        String awsPath, Object awsProfile, String awsRegion) {
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText("Checking settings file");
        MavenSettingsFileHandler mavenSettingsFileHandler = new MavenSettingsFileHandler(mavenSettingsFile);
        OperationOutput taskOutput = mavenSettingsFileHandler.locateServer(mavenServerId);
        if (taskOutput.ok && !progressIndicator.isCanceled()) {
            progressIndicator.setText("Obtaining AWS Auth Token");
            taskOutput = AWSInvoker.getAuthToken(domain, domainOwner, awsPath, awsProfile, awsRegion,
                    progressIndicator::isCanceled);
            if (taskOutput != null && taskOutput.ok && !progressIndicator.isCanceled()) {
                progressIndicator.setText("Updating settings file");
                taskOutput = mavenSettingsFileHandler.setPassword(taskOutput.output);
            }
        }
        return progressIndicator.isCanceled() ? null : taskOutput;
    }

    /**
     * Shows the results of the operation.
     *
     * @return True if we need to show the main dialog again
     */
    private boolean showResults(Project project, OperationOutput output) {
        if (output.ok) {
            Messages.showMessageDialog(project, "Auth token updated", MainDialog.COMPONENT_TITLE,
                    Messages.getInformationIcon());
        } else {
            Messages.showErrorDialog(project, output.output, MainDialog.COMPONENT_TITLE);
        }
        return !output.ok;
    }

    @Override
    protected void doOKAction() {
    }


    @Override
    protected void init() {
        super.init();
    }


    protected @NotNull JPanel createButtonsPanel(@NotNull List buttons) {
//        buttons.add(0, generateAllButton);
        return super.createButtonsPanel(buttons);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        JPanel ret = new JPanel(new BorderLayout(24, 0));
//        ret.add(centerPanel, BorderLayout.CENTER);
//        ret.add(getIconPanel(), BorderLayout.WEST);

        return ret;
    }


    @Override
    public void doCancelAction() {
        super.doCancelAction();
    }

}
