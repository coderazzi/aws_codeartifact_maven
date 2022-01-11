package net.coderazzi.aws_codeartifact_maven;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.*;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.util.ui.JBUI.Borders.empty;

class InputDialog extends DialogWrapper {

    public static final String COMPONENT_TITLE = "CodeArtifact + Maven";
    private static final String MAVEN_SERVER_USERNAME = "aws";

    private static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private static String LIGHT_ICON = "META-INF/pluginIcon.svg";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final DefaultComboBoxModel serverIdsModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup mavenServerId = new ComboBoxWithWidePopup(serverIdsModel);
    private final DefaultComboBoxModel awsProfileModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup awsProfile = new ComboBoxWithWidePopup(awsProfileModel);
    private final JTextField settingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);
    private Thread loadingServersThread, loadingProfilesThread;
    private InputDialogState state;

    private final PropertiesComponent properties;

    public InputDialog() {
        super(true); // use current window as parent
        properties = PropertiesComponent.getInstance();
        state = InputDialogState.getInstance();
        init();
        setTitle("Generate AWS CodeArtifact Credentials");
        setAutoAdjustable(true);
        setOKButtonText("Generate credentials");
    }

    public InputDialogState getState() {
        return state;
    }

    private void updatedMavenServerId() {
        Object s = mavenServerId.getSelectedItem();
        if (s instanceof String) {
            state.updateMavenServerId((String) s);
            domain.setText(state.getDomain(domain.getText()));
            domainOwner.setText(state.getDomainOwner(domainOwner.getText()));
            domain.setEnabled(true);
            domainOwner.setEnabled(true);
        } else {
            domain.setEnabled(false);
            domainOwner.setEnabled(false);
        }
    }

    private void updatedAwsProfile(){
        if (awsProfile.isEnabled()) {
            Object s = awsProfile.getSelectedItem();
            if (s instanceof String) {
                state.setAWSProfile((String) s);
            }
        }
    }


    private void updateRepositoryInformation(boolean reloadServersIfNeeded) {
        serverIdsModel.removeAllElements();
        Set<String> serverIds = state.getMavenServerIds();
        if (serverIds.isEmpty()) {
            if (reloadServersIfNeeded) {
                reloadServers();
                return;
            }
        } else {
            String currentId = state.getMavenServerId();
            for (String each : serverIds) {
                serverIdsModel.addElement(each);
            }
            serverIdsModel.setSelectedItem(currentId);
        }
        mavenServerId.setEnabled(true);
        updateGenerateCredentialsButtonState();
    }

    private void reloadServers() {
        final String filename = settingsFile.getText().trim();
        if (state.updateMavenSettingsFile(filename) || loadingServersThread == null) {
            serverIdsModel.removeAllElements();
            if (!filename.isEmpty()) {
                serverIdsModel.addElement(LOADING);
                mavenServerId.setEnabled(false);
                loadingServersThread = new Thread(() -> {
                    try {
                        updateServersInForeground(
                                new MavenSettingsFileHandler(filename).getServerIds(MAVEN_SERVER_USERNAME),
                                null
                        );
                    } catch (MavenSettingsFileHandler.GetServerIdsException ex) {
                        updateServersInForeground(new HashSet<>(), ex.getMessage());
                    }
                });
                loadingServersThread.start();
            }
        }
    }

    private void reloadAWSProfiles() {
        if (loadingProfilesThread == null) {
            awsProfileModel.removeAllElements();
            awsProfileModel.addElement(LOADING);
            awsProfile.setEnabled(false);
            loadingProfilesThread = new Thread(() -> {
                Set<String> profiles = null;
                String error = null;
                try {
                    profiles = AWSProfileHandler.getProfiles();
                } catch (AWSProfileHandler.GetProfilesException ex) {
                    error = ex.getMessage();
                }
                updateProfilesInForeground(profiles, error);
            });
            loadingProfilesThread.start();
        }
    }

    private void updateProfilesInForeground(Set<String> profiles, String error) {
        final Thread thread = Thread.currentThread();
        SwingUtilities.invokeLater(() -> {
            if (thread == loadingProfilesThread) {
                awsProfileModel.removeAllElements();
                loadingProfilesThread = null;
                if (error == null) {
                    state.updateAWSProfiles(profiles).forEach(awsProfileModel::addElement);
                    awsProfileModel.setSelectedItem(state.getAWSProfile());
                } else {
                    state.updateAWSProfiles(Collections.EMPTY_SET);
                    Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
                }
                awsProfile.setEnabled(true);
            }
        });
    }

    private void updateServersInForeground(Set<String> serverIds, String error) {
        final Thread thread = Thread.currentThread();
        SwingUtilities.invokeLater(() -> {
            if (thread == loadingServersThread) {
                state.updateMavenServerIds(serverIds);
                loadingServersThread = null;
                updateRepositoryInformation(false);
                if (error == null) {
                    if (serverIds.isEmpty()) {
                        Messages.showErrorDialog(settingsFile,
                                "Maven settings file does not define any server with username 'aws'",
                                COMPONENT_TITLE);
                    }
                } else {
                    Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
                }
            }
        });
    }

    private void updateGenerateCredentialsButtonState() {
        JButton ok = getButton((getOKAction()));
        if (ok != null) {
            ok.setEnabled(checkNonEmpty(domain)
                    && checkNonEmpty(domainOwner)
                    && checkHasSelection(mavenServerId)
                    && checkNonEmpty(awsPath)
            );
        }
    }

    private void handleTextFieldChange(JTextField check, Consumer<String> action) {
        check.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateGenerateCredentialsButtonState();
                action.accept(check.getText().trim());
            }
        });
    }

    private void handleComboBoxChange(ComboBoxWithWidePopup check, Runnable action) {
        check.addItemListener(x -> {
            if (x.getStateChange() == ItemEvent.SELECTED) {
                updateGenerateCredentialsButtonState();
                action.run();
            }
        });
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        TextFieldWithBrowseButton settingsFileBrowser = new TextFieldWithBrowseButton(settingsFile, x -> reloadServers());
        TextFieldWithBrowseButton awsPathBrowser = new TextFieldWithBrowseButton(awsPath);
        ComponentWithBrowseButton<ComboBoxWithWidePopup> mavenServerIdWrapper =
                new ComponentWithBrowseButton<>(mavenServerId, x -> reloadServers());
        ComponentWithBrowseButton<ComboBoxWithWidePopup> awsProfileWrapper =
                new ComponentWithBrowseButton<>(awsProfile, x -> reloadAWSProfiles());

        GridBag gridbag = new GridBag()
                .setDefaultWeightX(10.0)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)
                .setDefaultInsets(JBUI.insets(0, 0, AbstractLayout.DEFAULT_VGAP, AbstractLayout.DEFAULT_HGAP));

        JPanel centerPanel = new JPanel(new GridBagLayout());

        centerPanel.add(new TitledSeparator("Repository"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Domain:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(domain, gridbag.next().coverLine());
        centerPanel.add(getLabel("Domain owner:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(domainOwner, gridbag.next().coverLine());
        centerPanel.add(getLabel("Maven server id:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(mavenServerIdWrapper, gridbag.next().coverLine());
        centerPanel.add(getLabel("AWS profile:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(awsProfileWrapper, gridbag.next().coverLine());
        centerPanel.add(new TitledSeparator("Locations"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Maven settings file:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(settingsFileBrowser, gridbag.next().coverLine());
        centerPanel.add(getLabel("AWS cli path:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(awsPathBrowser, gridbag.next().coverLine());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0,0,24,0));

        settingsFile.setText(state.getMavenServerSettingsFile());
        settingsFile.addActionListener(x -> reloadServers()); // handle ENTER key
        awsPath.setText(state.getAWSPath());

        settingsFileBrowser.addBrowseFolderListener("Maven Settings File", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        awsPathBrowser.addBrowseFolderListener("aws Executable Location", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        mavenServerIdWrapper.setButtonIcon(AllIcons.Actions.Refresh);
        awsProfileWrapper.setButtonIcon(AllIcons.Actions.Refresh);

        handleTextFieldChange(awsPath, x -> state.updateAwsPath(x));
        handleTextFieldChange(domainOwner, x -> state.updateDomainOwner(x));
        handleTextFieldChange(domain, x -> state.updateDomain(x));
        handleComboBoxChange(mavenServerId, this::updatedMavenServerId);
        handleComboBoxChange(awsProfile, this::updatedAwsProfile);

        updateRepositoryInformation(true);

        JPanel ret = new JPanel(new BorderLayout(24, 0));
        ret.add(centerPanel, BorderLayout.CENTER);
        ret.add(getIconPanel(), BorderLayout.WEST);

        if (state.shouldLoadProfiles()) {
            reloadAWSProfiles();
        } else {
            String profile = state.getAWSProfile();
            Set<String> profiles = state.getAWSProfiles();
            // next call will modify the profile, that is why we store it beforehand
            profiles.forEach(awsProfileModel::addElement);
            awsProfileModel.setSelectedItem(profile);
        }

        return ret;
    }

    private JComponent getIconPanel() {
        String resource = ColorUtil.isDark(getOwner().getBackground()) ? DARK_ICON : LIGHT_ICON;
        URL url = getClass().getClassLoader().getResource(resource);
        if (url != null) {
            try {
                return new JLabel(new ImageIcon(SVGLoader.load(url, 2.5f)));
            } catch (IOException ex) {
            }
        }
        return new JLabel();
    }


    private JComponent getLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(empty(0, 5, 2, 0));
        return label;
    }

    @Override
    public void doCancelAction() {
        loadingServersThread = null;
        super.doCancelAction();
    }

    private boolean checkNonEmpty(JTextField check) {
        return !check.getText().trim().isEmpty();
    }

    private boolean checkHasSelection(ComboBoxWithWidePopup check) {
        return check.isEnabled() && check.getSelectedItem() != null;
    }

    private static Object LOADING = new Object() {
        @Override
        public String toString() {
            return "Loading ...";
        }
    };

}