package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.coderazzi.aws_codeartifact_maven.state.AwsConfiguration;
import net.coderazzi.aws_codeartifact_maven.utils.*;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ui.JBUI.Borders.empty;

@SuppressWarnings({"unchecked"})
public class GenerationDialog extends DialogWrapper {

    public static final String BACK_TEXT = "Back";

    final private static int MAX_ERROR_MESSAGE = 34;
    final private static long ARTIFICIAL_WAIT_MS = 100;
    private final Pattern HTML_PATTERN = Pattern.compile("(<html>.*?)(?:<br>.*|</html>)");
    private final Project project;
    private final String mavenSettingsFile;
    private final String awsPath;
    private final Map<String, ConfigurationRow> configurations = new TreeMap<>();
    private final boolean isGenerateForAll;
    private boolean completed, closeDialog;
    private volatile boolean cancelled;

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

    public boolean isCancelled(){
        return cancelled;
    }

    public Project getProject() {
        return project;
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
            setCancelButtonText(BACK_TEXT);
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
            ConfigurationRow confRow = configurations.get(name);
            if (!cancelled) {
                try {
                    checkNotEmptyString(configuration.domain, "domain");
                    checkNotEmptyString(configuration.domainOwner, "domainOwner");
                    checkNotEmptyString(configuration.mavenServerId, "mavenServerId");
                    setMessage(confRow, state, "Checking settings file");
                    MavenSettingsFileHandler mavenSettingsFileHandler = new MavenSettingsFileHandler(mavenSettingsFile);
                    mavenSettingsFileHandler.locateServer(configuration.mavenServerId);
                    if (!cancelled) {
                        InvokerController controller = new InvokerController(this) {
                            @Override
                            public void showMessage(@NotNull String shortMessage, @Nullable String popupMessage) {
                                SwingUtilities.invokeLater(() -> {
                                    setMessage(confRow, TaskState.RUNNING, shortMessage);
                                    if (popupMessage!=null) {
                                        Messages.showInfoMessage(project, popupMessage, MainDialog.COMPONENT_TITLE);
                                    }
                                });
                            }
                        };
                        setMessage(confRow, state, "Obtaining AWS Auth Token");
                        String token = new AWSInvoker(controller, configuration.domain, configuration.domainOwner,
                                awsPath, configuration.profile, configuration.region).getAuthToken();
                        setMessage(confRow, state, "Updating settings file");
                        mavenSettingsFileHandler.setPassword(token);
                        setMessage(confRow, state = TaskState.COMPLETED, "Auth token generated");
                    }
                } catch (OperationException iex) {
                    if (!cancelled) {
                        setMessage(confRow, state = TaskState.ERROR, iex.getMessage());
                    }
                }
            }
            if (state == TaskState.RUNNING && cancelled) {
                setMessage(confRow, state = TaskState.CANCELLED, "Cancelled");
            }
        }
        return state;
    }

    private void setMessage(ConfigurationRow row, TaskState taskState, String message) {
        // cannot use here ApplicationManager.getApplication().invokeLater, does nothing
        if (SwingUtilities.isEventDispatchThread()) {
            String shortMessage;
            boolean isLongMessage;
            JLabel label = row.label;
            boolean error = taskState == TaskState.ERROR;
            Matcher html = HTML_PATTERN.matcher(message);
            if (html.matches()) {
                isLongMessage = true;
                shortMessage = html.group(1);
            } else {
                isLongMessage = message.length() > MAX_ERROR_MESSAGE;
                shortMessage = isLongMessage?  message.substring(0, MAX_ERROR_MESSAGE) + "..." : message;
            }
            label.setText(shortMessage);
            if (taskState.icon != label.getIcon()) {
                label.setIcon(taskState.icon);
            }
            if (isLongMessage || error) {
                if (row.mouseListener == null) {
                    label.addMouseListener(row.mouseListener = new RowMouseAdapter());
                }
                row.mouseListener.isError = error;
                row.mouseListener.wholeMessage = message;
                if (error && configurations.size()==1) {
                    Messages.showErrorDialog(project, message, MainDialog.COMPONENT_TITLE);
                }
            } else if (row.mouseListener != null) {
                label.removeMouseListener(row.mouseListener);
                row.mouseListener = null;
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                try {
                    setMessage(row, taskState, message);
                    if (taskState != TaskState.RUNNING) Thread.sleep(ARTIFICIAL_WAIT_MS);
                } catch (Exception ex) {
                    // nothing to do at this stage
                }
            });
        }
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
            centerPanel.add(row.label = createLabel(text), c);
            row.label.setIconWithAlignment(icon, SwingConstants.LEFT, SwingConstants.CENTER);
            row.label.setCopyable(false);
        }

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double maxWidth = screenSize.getWidth() -64, maxHeight = screenSize.getHeight() - 128;
        Dimension size = centerPanel.getMinimumSize();
        if (size.getHeight() > maxHeight || size.getWidth() > maxWidth) {
            JScrollPane scroll = new JScrollPane(centerPanel,
                    JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            Dimension sizeScrollNoBar = scroll.getPreferredSize();
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            Dimension sizeScrollBar = scroll.getPreferredSize();
            scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            size.setSize(
                    Math.min(maxWidth, size.getWidth() + sizeScrollBar.getWidth() - sizeScrollNoBar.getWidth()),
                    Math.min(maxHeight, size.getHeight() + sizeScrollBar.getHeight() - sizeScrollNoBar.getHeight())
            );
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.add(scroll, BorderLayout.CENTER);
            wrapper.setPreferredSize(size);
            return wrapper;
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
        if (completed || cancelled) {
            super.doCancelAction();
        } else {
            cancelled = true;
            getButton(getCancelAction()).setText(BACK_TEXT);
        }
    }

    @Override
    public boolean isOK() {
        return super.isOK() && completed;
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

    private static class ConfigurationRow {
        final AwsConfiguration configuration;
        JBLabel label;
        RowMouseAdapter mouseListener;
        ConfigurationRow(AwsConfiguration configuration) { this.configuration = configuration; }
    }

    private class RowMouseAdapter extends MouseAdapter{
        boolean isError;
        String wholeMessage;
        @Override
        public void mouseClicked(MouseEvent e) {
            if (isError) {
                Messages.showErrorDialog(project, wholeMessage, MainDialog.COMPONENT_TITLE);
            } else {
                Messages.showInfoMessage(project, wholeMessage, MainDialog.COMPONENT_TITLE);
            }
        }
    }

}
