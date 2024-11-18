package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.coderazzi.aws_codeartifact_maven.state.AwsConfiguration;
import net.coderazzi.aws_codeartifact_maven.utils.AWSInvoker;
import net.coderazzi.aws_codeartifact_maven.utils.MavenSettingsFileHandler;
import net.coderazzi.aws_codeartifact_maven.utils.MfaCodeValidator;
import net.coderazzi.aws_codeartifact_maven.utils.OperationException;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.intellij.util.ui.JBUI.Borders.empty;

@SuppressWarnings({"unchecked"})
class GenerationDialog extends DialogWrapper implements AWSInvoker.BackgroundController {

    private static class ConfigurationRow {
        final AwsConfiguration configuration;
        JBLabel message;
        ConfigurationRow(AwsConfiguration configuration) { this.configuration = configuration; }
    }

    final private static int MAX_ERROR_MESSAGE = 32;
    final private static long ARTIFICIAL_WAIT_MS = 100;
    private final Project project;
    private final String mavenSettingsFile;
    private final String awsPath;
    private final Map<String, ConfigurationRow> configurations = new TreeMap<>();
    private final boolean isGenerateForAll;
    private boolean cancelled, completed, closeDialog;


    public GenerationDialog(final Project project,
                            final Configuration state) {
        super(project, true); // use current window as parent
        this.project = project;
        mavenSettingsFile = state.getMavenServerSettingsFile();
        awsPath=state.getAWSPath();
        isGenerateForAll = state.isGenerateForAll();
        for (String name : state.getConfigurationNames()) {
            if (isGenerateForAll || state.getConfigurationName().equals(name)) {
                configurations.put(name, new ConfigurationRow(state.getConfiguration(name)));
            }
        }
        init();
        setTitle("Generating AWS Auth Tokens");
        setAutoAdjustable(true);
        setOKButtonText("Close");

        getWindow().addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                ApplicationManager.getApplication().executeOnPooledThread(GenerationDialog.this::launch);
            }
        });
        getWindow().addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                super.windowGainedFocus(e);
                if (closeDialog) {
                    doCancelAction();
                }
            }
        });
    }

    private void launch(){
        boolean errors = false;
        for (Map.Entry<String, ConfigurationRow> entry : configurations.entrySet()) {
            errors = requestToken(entry.getKey(), entry.getValue().configuration) == TaskState.ERROR || errors;
        }
        completed = true;
        generationComplete(errors);
    }

    private void generationComplete(final boolean withErrors){
        SwingUtilities.invokeLater(()->{
            setCancelButtonText("Back");
            getCancelAction().setEnabled(true);
            if (withErrors) {
                // if there are errors, just for one configuration, the error is shown directly
                // in that case, when that window closes, we can close as well this window
                closeDialog = configurations.size() == 1;
            } else {
                getOKAction().setEnabled(true);
            }
        });
    }

    private TaskState requestToken(String name, AwsConfiguration configuration) {
        TaskState state = TaskState.RUNNING;
        if (configuration.enabled || !isGenerateForAll) {
            JLabel messageField = configurations.get(name).message;
            if (!cancelled) {
                try {
                    checkNotEmptyString(configuration.domain, "domain");
                    checkNotEmptyString(configuration.domainOwner, "domainOwner");
                    checkNotEmptyString(configuration.mavenServerId, "mavenServerId");
                    setMessage(messageField, state, "Checking settings file");
                    MavenSettingsFileHandler mavenSettingsFileHandler = new MavenSettingsFileHandler(mavenSettingsFile);
                    mavenSettingsFileHandler.locateServer(configuration.mavenServerId);
                    if (!cancelled) {
                        setMessage(messageField, state, "Obtaining AWS Auth Token");
                        String token = AWSInvoker.getAuthToken(configuration.domain, configuration.domainOwner,
                                awsPath, configuration.profile, configuration.region, this);
                        if (!cancelled) {
                            setMessage(messageField, state, "Updating settings file");
                            mavenSettingsFileHandler.setPassword(token);
                            setMessage(messageField, state = TaskState.COMPLETED, "Auth token generated");
                        }
                    }
                } catch (OperationException iex) {
                    setMessage(messageField, state = TaskState.ERROR, iex.getMessage());
                }
            }
            if (state == TaskState.RUNNING && cancelled) {
                setMessage(messageField, state = TaskState.CANCELLED, "Cancelled");
            }
        }
        return state;
    }


    private void setMessage(JLabel label, TaskState taskState, String message) {
        // cannot use here ApplicationManager.getApplication().invokeLater, does nothing
        try {
            SwingUtilities.invokeLater(() -> {
                label.setText(message.length() > MAX_ERROR_MESSAGE?
                        message.substring(0, MAX_ERROR_MESSAGE) + "..." : message);
                if (taskState.icon != label.getIcon()) {
                    label.setIcon(taskState.icon);
                    if (taskState == TaskState.ERROR) {
                        label.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                Messages.showErrorDialog(project, message, MainDialog.COMPONENT_TITLE);
                            }
                        });
                        if (configurations.size() == 1) {
                            // show the error immediately
                            Messages.showErrorDialog(project, message, MainDialog.COMPONENT_TITLE);
                        }
                    }
                }
            });
            if (taskState != TaskState.RUNNING)  Thread.sleep(ARTIFICIAL_WAIT_MS);
        } catch(Exception ex){}
    }

    protected @NotNull JPanel createButtonsPanel(@NotNull List buttons) {
        JPanel ret = super.createButtonsPanel(buttons);
        getOKAction().setEnabled(false);
        return ret;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.ipadx = 12;
        c.ipady = 8;
        centerPanel.add(createHeaderLabel("Configuration"), c);
        centerPanel.add(createHeaderLabel("Maven server id"), c);
        centerPanel.add(createHeaderLabel("Profile"), c);
        c.weightx = 1.0;
        JBLabel status = createHeaderLabel("status");
        status.setMinimumSize(new Dimension(240, 0));
        centerPanel.add(status, c);

        for (Map.Entry<String, ConfigurationRow> entry : configurations.entrySet()) {
            c.weightx = 0.0;
            c.gridy = c.gridy + 1;
            ConfigurationRow row = entry.getValue();
            AwsConfiguration configuration = row.configuration;
            centerPanel.add(createLabel(entry.getKey()), c);
            centerPanel.add(createLabel(configuration.mavenServerId), c);
            centerPanel.add(createLabel(configuration.profile), c);
            c.weightx = 1.0;
            String text;
            Icon icon;
            if (row.configuration.enabled || !isGenerateForAll) {
                text = "";
                icon = AllIcons.General.SeparatorH;
            } else {
                text = "Disabled";
                icon = AllIcons.General.Warning;
            }
            centerPanel.add(row.message = createLabel(text), c);
            row.message.setIconWithAlignment(icon, SwingConstants.LEFT, SwingConstants.CENTER);
            row.message.setCopyable(false);
        }
        return centerPanel;
    }

    private JBLabel createLabel(String text) {
        JBLabel label = new JBLabel(text==null? "" : text);
        label.setComponentStyle(UIUtil.ComponentStyle.LARGE);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(empty(0, 5, 2, 0));
        return label;
    }

    private JBLabel createHeaderLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(empty(0, 5, 2, 0));
        return label;
    }

    @Override
    public void doCancelAction() {
        if (completed) {
            super.doCancelAction();
        } else {
            Action cancelAction = getCancelAction();
            if (cancelAction.isEnabled()) {
                cancelled = true;
                getButton(cancelAction).setText("Cancelling....");
                getOKAction().setEnabled(true);
                cancelAction.setEnabled(false);
            }
        }
    }

    @Override
    public boolean isOK() {
        return super.isOK() && completed;
    }

    @Override
    public String requestMfaCode(String request)  throws OperationException{
        final String []ret = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                Messages.InputDialog dialog = new Messages.InputDialog(project, request, "AWS input request", null, "", new MfaCodeValidator());
                if (dialog.showAndGet()) {
                    ret[0] = dialog.getInputString();
                }
            });
        } catch(Exception iex) {
            throw new OperationException("Internal plugin error");
        }
        if (ret[0] == null || ret[0].isEmpty()) {
            throw new OperationException("No MFA code provided");
        }
        return ret[0];
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    private static void checkNotEmptyString(String text, String description) throws OperationException {
        if (text==null || text.isBlank()) {
            throw new OperationException("Field %s is not defined", description);
        }
    }

    private enum TaskState {
        RUNNING(AllIcons.Toolwindows.ToolWindowRun),
        CANCELLED(AllIcons.General.Warning),
        ERROR(AllIcons.General.Error),
        COMPLETED(AllIcons.General.InspectionsOK);
        TaskState(Icon icon){
            this.icon = icon;
        }
        final public Icon icon;
    }

}
