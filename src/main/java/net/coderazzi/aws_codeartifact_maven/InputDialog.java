package net.coderazzi.aws_codeartifact_maven;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.util.ui.JBUI.Borders.empty;

@SuppressWarnings("unchecked")
class InputDialog extends DialogWrapper {

    public static final String COMPONENT_TITLE = "CodeArtifact + Maven";
    private static final String MAVEN_SERVER_USERNAME = "aws";

    private final static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private final static String LIGHT_ICON = "META-INF/pluginIcon.svg";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final DefaultComboBoxModel regionsModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup region = new ComboBoxWithWidePopup(regionsModel);
    private final DefaultComboBoxModel serverIdsModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup mavenServerId = new ComboBoxWithWidePopup(serverIdsModel);
    private final DefaultComboBoxModel awsProfileModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup awsProfile = new ComboBoxWithWidePopup(awsProfileModel);
    private final JTextField settingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);
    private Thread loadingServersThread, loadingProfilesThread;
    private final InputDialogState state;

    public InputDialog() {
        super(true); // use current window as parent
        state = InputDialogState.getInstance();
        init();
        setTitle("Generate AWS CodeArtifact Credentials");
        setAutoAdjustable(true);
        setOKButtonText("Generate Credentials");
    }

    public InputDialogState getState() {
        return state;
    }

    /**
     * Called whenever the user changes the maven server id
     */
    private void updatedMavenServerId() {
        Object s = mavenServerId.getSelectedItem();
        if (s instanceof String) {
            state.updateMavenServerId((String) s);
            domain.setText(state.getDomain(domain.getText()));
            domainOwner.setText(state.getDomainOwner(domainOwner.getText()));
            setSelectedRegion(state.getRegion(getSelectedRegion()));
            domain.setEnabled(true);
            domainOwner.setEnabled(true);
        } else {
            domain.setEnabled(false);
            domainOwner.setEnabled(false);
        }
    }

    /**
     * Called whenever the user changes the AWS profile
     */
    private void updatedAwsProfile() {
        if (awsProfile.isEnabled()) {
            Object s = awsProfile.getSelectedItem();
            if (s instanceof String) {
                state.setProfile((String) s);
            }
        }
    }


    /**
     * Called whenever the user changes the region
     */
    private void updatedRegion() {
        Object s = region.getSelectedItem();
        if (s != null) {
            state.updateRegion(s instanceof String ? (String) s : "");
        }
    }


    /**
     * Displays all information related to the repository.
     *
     * @param reloadServersIfNeeded set to true to load servers from maven settings file IF there are none yet
     */
    private void showRepositoryInformation(boolean reloadServersIfNeeded) {
        serverIdsModel.removeAllElements();
        Set<String> serverIds = state.getMavenServerIds();
        if (serverIds.isEmpty()) {
            if (reloadServersIfNeeded) {
                reloadServersInBackground();
                return;
            }
        } else {
            String currentId = state.getMavenServerId();
            serverIds.forEach(serverIdsModel::addElement);
            serverIdsModel.setSelectedItem(currentId);
        }
        mavenServerId.setEnabled(true);
        updateGenerateCredentialsButtonState();
    }

    private void showProfileInformation() {
        Set<String> profiles = state.getProfiles();
        if (profiles.isEmpty()) {
            // next call will always find profiles to show
            reloadProfilesInBackground();
        } else {
            String profile = state.getProfile();
            // next call will modify the profile, that is why we store it beforehand
            profiles.forEach(awsProfileModel::addElement);
            awsProfile.setEnabled(true);
            awsProfileModel.setSelectedItem(profile);
        }
        updateGenerateCredentialsButtonState();
    }

    /**
     * Starts a new thread to load the servers from the maven settings file.
     * It does nothing if there is already a reload in progress for the same settings file
     */
    private void reloadServersInBackground() {
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

    /**
     * Starts a new thread to load the profiles from the aws config file.
     * It does nothing if there is already a reload in progress
     */
    private void reloadProfilesInBackground() {
        if (loadingProfilesThread == null) {
            awsProfile.setEnabled(false);
            awsProfileModel.removeAllElements();
            awsProfileModel.addElement(LOADING);
            loadingProfilesThread = new Thread(() -> {
                Set<String> profiles;
                String error = null;
                try {
                    profiles = AWSProfileHandler.getProfiles();
                } catch (AWSProfileHandler.GetProfilesException ex) {
                    profiles = AWSProfileHandler.getDefaultProfiles();
                    error = ex.getMessage();
                }
                updateProfilesInForeground(profiles, error);
            });
            loadingProfilesThread.start();
        }
    }

    /**
     * Displays the information for the aws profiles, once loaded in the background
     */
    private void updateProfilesInForeground(Set<String> profiles, String error) {
        ApplicationManager.getApplication().invokeLater(() -> {
            loadingProfilesThread = null;
            awsProfileModel.removeAllElements();
            state.setProfiles(profiles);
            showProfileInformation();
            awsProfile.requestFocus();
            if (error != null) {
                Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
            }
        });
    }

    private void updateServersInForeground(Set<String> serverIds, String error) {
        final Thread thread = Thread.currentThread();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (thread == loadingServersThread) {
                state.updateMavenServerIds(serverIds);
                loadingServersThread = null;
                showRepositoryInformation(false);
                if (error == null) {
                    if (serverIds.isEmpty()) {
                        Messages.showErrorDialog(settingsFile,
                                "Maven settings file does not define any server with username 'aws'",
                                COMPONENT_TITLE);
                    } else {
                        mavenServerId.requestFocus();
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
                    && checkHasSelection(awsProfile)
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

    @Override
    protected void init() {
        super.init();
        regionsModel.addElement(InputDialogState.DEFAULT_PROFILE_REGION);
        state.getValidRegions().forEach(regionsModel::addElement);
        handleTextFieldChange(awsPath, state::updateAwsPath);
        handleTextFieldChange(domainOwner, state::updateDomainOwner);
        handleTextFieldChange(domain, state::updateDomain);
        handleComboBoxChange(mavenServerId, this::updatedMavenServerId);
        handleComboBoxChange(awsProfile, this::updatedAwsProfile);
        handleComboBoxChange(region, this::updatedRegion);
        showProfileInformation();
        showRepositoryInformation(true);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        TextFieldWithBrowseButton settingsFileBrowser = new TextFieldWithBrowseButton(settingsFile, x -> reloadServersInBackground());
        TextFieldWithBrowseButton awsPathBrowser = new TextFieldWithBrowseButton(awsPath);
        ComponentWithBrowseButton<ComboBoxWithWidePopup> mavenServerIdWrapper =
                new ComponentWithBrowseButton<>(mavenServerId, x -> reloadServersInBackground());
        ComponentWithBrowseButton<ComboBoxWithWidePopup> awsProfileWrapper =
                new ComponentWithBrowseButton<>(awsProfile, x -> reloadProfilesInBackground());

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
        centerPanel.add(getLabel("Region:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(region, gridbag.next().coverLine());
        centerPanel.add(new TitledSeparator("Locations"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Maven settings file:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(settingsFileBrowser, gridbag.next().coverLine());
        centerPanel.add(getLabel("AWS cli path:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(awsPathBrowser, gridbag.next().coverLine());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));

        settingsFile.setText(state.getMavenServerSettingsFile());
        settingsFile.addActionListener(x -> reloadServersInBackground()); // handle ENTER key
        awsPath.setText(state.getAWSPath());

        settingsFileBrowser.addBrowseFolderListener("Maven Settings File", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        awsPathBrowser.addBrowseFolderListener("aws Executable Location", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        mavenServerIdWrapper.setButtonIcon(AllIcons.Actions.Refresh);
        awsProfileWrapper.setButtonIcon(AllIcons.Actions.Refresh);


        JPanel ret = new JPanel(new BorderLayout(24, 0));
        ret.add(centerPanel, BorderLayout.CENTER);
        ret.add(getIconPanel(), BorderLayout.WEST);

        return ret;
    }

    private JComponent getIconPanel() {
        JLabel label = new JLabel();
        try {
            String resource = ColorUtil.isDark(getOwner().getBackground()) ? DARK_ICON : LIGHT_ICON;
            URL url = getClass().getClassLoader().getResource(resource);
            if (url != null) {
                label.setIcon(IconLoader.findIcon(url));
            }
        } catch (Exception ex){
            // nothing to do here, just a missing icon
        }
        return label;
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
        return !check.getText().isBlank();
    }

    private boolean checkHasSelection(ComboBoxWithWidePopup check) {
        return check.isEnabled() && check.getSelectedItem() != null;
    }

    private String getSelectedRegion() {
        Object ret = region.getSelectedItem();
        return ret == null ? InputDialogState.DEFAULT_PROFILE_REGION : ret.toString();
    }

    private void setSelectedRegion(String s) {
        if (s == null || s.isEmpty()) {
            region.setSelectedItem(InputDialogState.DEFAULT_PROFILE_REGION);
        } else {
            region.setSelectedItem(s);
        }
    }

    private static final Object LOADING = new Object() {
        @Override
        public String toString() {
            return "Loading ...";
        }
    };

}
