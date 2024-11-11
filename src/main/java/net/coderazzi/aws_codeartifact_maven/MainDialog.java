package net.coderazzi.aws_codeartifact_maven;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.ui.*;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.util.ui.JBUI.Borders.empty;

@SuppressWarnings({"unchecked", "rawtypes"})
class MainDialog extends DialogWrapper {

    public static final String COMPONENT_TITLE = "CodeArtifact + Maven";
    private static final String MAVEN_SERVER_USERNAME = "aws";

    private final static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private final static String LIGHT_ICON = "META-INF/pluginIcon.svg";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final DefaultComboBoxModel configurationsModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel regionsModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel serverIdsModel = new DefaultComboBoxModel();
    private final DefaultComboBoxModel profileModel = new DefaultComboBoxModel();
    private final ComboBoxWithWidePopup configurationComboBox = new ComboBoxWithWidePopup(configurationsModel);
    private final ComboBoxWithWidePopup regionComboBox = new ComboBoxWithWidePopup(regionsModel);
    private final ComboBoxWithWidePopup serverIdComboBox = new ComboBoxWithWidePopup(serverIdsModel);
    private final ComboBoxWithWidePopup profileComboBox = new ComboBoxWithWidePopup(profileModel);
    private final JBCheckBox generateAllCheckBox = new JBCheckBox("Generate Tokens for all configurations");

    private final JBLabel serverWarningLabel, serverWarningEmptyLabel;
    private final JBLabel profileWarningLabel, profileWarningEmptyLabel;

    private final JTextField settingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);
    private final JBCheckBox enabledCheckbox = new JBCheckBox();
    private Thread loadingServersThread, loadingProfilesThread;
    private final Project project;
    private final Configuration state;

    private ComponentWithBrowseButton<ComponentWithBrowseButton> removeConfigurationsComponent;

    public MainDialog(Project project) {
        super(true); // use current window as parent
        this.project = project;
        state = new Configuration();
        serverWarningLabel = getLabel("invalid server id, not found in settings file");
        serverWarningEmptyLabel = getLabel("");
        serverWarningLabel.setIcon(AllIcons.General.Error);
        serverWarningLabel.setVisible(false);
        serverWarningEmptyLabel.setVisible(false);
        profileWarningLabel = getLabel("invalid profile");
        profileWarningEmptyLabel = getLabel("");
        profileWarningLabel.setIcon(AllIcons.General.Error);
        profileWarningLabel.setVisible(false);
        profileWarningEmptyLabel.setVisible(false);
        init();
        setTitle("AWS CodeArtifact Auth Tokens Generation");
        setAutoAdjustable(true);
        setOKButtonText("Generate Auth Token");
        setCancelButtonText("Close");
    }

    @Override
    protected void doOKAction() {
        if (this.getOKAction().isEnabled()) {
            new GenerationDialog(project, state, true).show();
        }
    }

    /**
     * Called whenever the user changes the maven server id
     */
    private void updatedMavenServerId() {
        Object s = serverIdComboBox.getSelectedItem();
        boolean bad = false;
        if (s == null) {
            state.getCurrentConfiguration().mavenServerId=null;
        } else if (s instanceof String serverId) {
            state.getCurrentConfiguration().mavenServerId=serverId;
            bad = !state.isDefinedMavenServerId(serverId);
        }
        serverWarningLabel.setVisible(bad);
        serverWarningEmptyLabel.setVisible(bad);
    }

    /**
     * Called whenever the user changes the AWS profile
     */
    private void updatedAwsProfile() {
        Object s = profileComboBox.getSelectedItem();
        boolean bad = false;
        if (s instanceof String profile) {
            state.getCurrentConfiguration().profile =profile;
            bad = !state.isValidProfileName(profile);
        }
        profileWarningLabel.setVisible(bad);
        profileWarningEmptyLabel.setVisible(bad);
    }

    /**
     * Called whenever the user changes the region
     */
    private void updatedRegion() {
        Object s = regionComboBox.getSelectedItem();
        if (s != null) {
            state.getCurrentConfiguration().region = s instanceof String ? (String) s : "";
        }
    }

    private void updateConfiguration(){
        Object s = configurationComboBox.getSelectedItem();
        if (s != null){
            state.setConfigurationName(s.toString());
            enabledCheckbox.setSelected(state.getCurrentConfiguration().enabled);
            domain.setText(state.getCurrentConfiguration().domain);
            domainOwner.setText(state.getCurrentConfiguration().domainOwner);
            setSelectedRegion(state.getCurrentConfiguration().region);
            showRepositoryInformation(false);
            showProfileInformation();
        }
    }

    /**
     * Displays all information related to the repository.
     *
     * @param reloadServersIfNeeded set to true to load servers from maven settings file IF there are none yet
     */
    private void showRepositoryInformation(boolean reloadServersIfNeeded) {
        String current = state.getCurrentConfiguration().mavenServerId;
        serverIdsModel.removeAllElements();
        Set<String> serverIds = state.getDefinedMavenServerIds();
        if (serverIds.isEmpty()) {
            if (reloadServersIfNeeded) {
                reloadServersInBackground();
                return;
            }
        }
        serverIdComboBox.setEnabled(false);
        serverIds.forEach(serverIdsModel::addElement);
        serverIdsModel.setSelectedItem(current);
        serverIdComboBox.setEnabled(true);
        generateAllCheckBox.setEnabled(state.isMultipleGenerationEnabled());
        removeConfigurationsComponent.setButtonEnabled(state.hasMultipleConfigurationsDefined());
        updateGenerationButtonState();
    }

    private void showConfigurationInformation(boolean reloadServersIfNeeded) {
        String current = state.getConfigurationName();
        configurationsModel.removeAllElements();
        state.getConfigurationNames().forEach(configurationsModel::addElement);
        configurationsModel.setSelectedItem(current);
        showRepositoryInformation(reloadServersIfNeeded);
    }

    private void showProfileInformation() {
        Set<String> profiles = state.getProfileNames();
        if (profiles.isEmpty()) {
            // next call will always find profiles to show
            reloadProfilesInBackground();
        } else {
            String current = state.getCurrentConfiguration().profile;
            profileModel.removeAllElements();
            profiles.forEach(profileModel::addElement);
            profileModel.setSelectedItem(current);
        }
        updateGenerationButtonState();
    }

    /**
     * Starts a new thread to load the servers from the maven settings file.
     * It does nothing if there is already a reload in progress for the same settings file
     */
    private void reloadServersInBackground() {
        final String filename = settingsFile.getText().trim();
        if (!filename.equals(state.getMavenServerSettingsFile())  || loadingServersThread == null) {
            state.setMavenSettingsFile(filename);
            String current = state.getCurrentConfiguration().mavenServerId;
            serverIdsModel.removeAllElements();
            if (!filename.isEmpty()) {
                serverIdsModel.addElement(LOADING);
                serverIdComboBox.setEnabled(false);
                loadingServersThread = new Thread(() -> {
                    try {
                        Set<String> ids = new MavenSettingsFileHandler(filename).getServerIds(MAVEN_SERVER_USERNAME);
                        String error = ids.isEmpty() ? "Maven settings file does not define any server with username 'aws'"
                                : null;
                        updateServersInForeground(current, ids, error);
                    } catch (MavenSettingsFileHandler.GetServerIdsException ex) {
                        updateServersInForeground(current, new HashSet<>(), ex.getMessage());
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
            profileComboBox.setEnabled(false);
            profileModel.removeAllElements();
            profileModel.addElement(LOADING);
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
        SwingUtilities.invokeLater(() -> {
            loadingProfilesThread = null;
            state.setProfileNames(profiles);
            showProfileInformation();
            profileComboBox.setEnabled(true);
            profileComboBox.requestFocus();
            if (error != null) {
                Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
            }
        });
    }

    private void updateServersInForeground(String originalSetting, Set<String> serverIds, String error) {
        final Thread thread = Thread.currentThread();
        SwingUtilities.invokeLater(() -> {
            if (thread == loadingServersThread) {
                state.getCurrentConfiguration().mavenServerId=originalSetting;
                state.setDefinedMavenServerIds(serverIds);
                loadingServersThread = null;
                showRepositoryInformation(false);
                if (error == null) {
                    serverIdComboBox.requestFocus();
                } else {
                    Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
                }
            }
        });
    }

    private void updateGenerationButtonState() {
        JButton ok = getButton((getOKAction()));
        if (ok != null) {
            ok.setEnabled(checkNonEmpty(domain)
                    && checkNonEmpty(domainOwner)
                    && checkHasSelection(serverIdComboBox)
                    && checkHasSelection(profileComboBox)
                    && checkNonEmpty(awsPath)
                    && !serverWarningLabel.isVisible()
                    && !profileWarningLabel.isVisible()
            );
        }
    }

    private void handleTextFieldChange(JTextField check, Consumer<String> action) {
        check.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateGenerationButtonState();
                action.accept(check.getText().trim());
            }
        });
    }

    private void handleComboBoxChange(ComboBoxWithWidePopup check, Runnable action) {
        check.addItemListener(x -> {
            updateGenerationButtonState();
            action.run();
        });
    }

    private void createConfiguration(ActionEvent actionEvent) {
        final ConfigurationNameDialog dialog = new ConfigurationNameDialog(null, state.getConfigurationNames());
        if (dialog.showAndGet()) {
            state.addConfiguration(dialog.getName());
            showConfigurationInformation(false);
        }
    }

    private void renameConfiguration() {
        final ConfigurationNameDialog dialog = new ConfigurationNameDialog(state.getConfigurationName(), state.getConfigurationNames());
        if (dialog.showAndGet() && state.renameConfiguration(dialog.getName())) {
            showConfigurationInformation(false);
        }
    }

    private void deleteConfiguration(ActionEvent event) {
        if (ConfirmationDialog.requestForConfirmation(VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION,
                project,
                "Are you sure to delete this configuration",
                "Delete Configuration",
                AllIcons.General.QuestionDialog)
        ) {
            state.deleteConfiguration();
            showConfigurationInformation(false);
        }
    }

    @Override
    protected void init() {
        super.init();
        regionsModel.addElement(Configuration.DEFAULT_PROFILE_REGION);
        Configuration.getValidRegions().forEach(regionsModel::addElement);
        handleTextFieldChange(awsPath, state::setAwsPath);
        handleTextFieldChange(domainOwner, x -> state.getCurrentConfiguration().domainOwner = x);
        handleTextFieldChange(domain, x -> state.getCurrentConfiguration().domain = x);
        handleComboBoxChange(serverIdComboBox, this::updatedMavenServerId);
        handleComboBoxChange(profileComboBox, this::updatedAwsProfile);
        handleComboBoxChange(regionComboBox, this::updatedRegion);
        handleComboBoxChange(configurationComboBox, this::updateConfiguration);
        enabledCheckbox.addItemListener(this::updateEnableConfiguration);
        generateAllCheckBox.addItemListener(x-> state.setGenerateForAll(generateAllCheckBox.isSelected()));
        showConfigurationInformation(true);
    }

    private void updateEnableConfiguration(ItemEvent e) {
        state.getCurrentConfiguration().enabled = enabledCheckbox.isSelected();
        generateAllCheckBox.setEnabled(state.isMultipleGenerationEnabled());
    }


    @Override
    protected JComponent createSouthPanel() {
        JComponent parent = super.createSouthPanel();
        JPanel wrapped = new JPanel(new BorderLayout(12, 0));
        wrapped.add(generateAllCheckBox, BorderLayout.WEST);
        wrapped.add(parent, BorderLayout.EAST);
        generateAllCheckBox.setSelected(state.isGenerateForAll());
        JPanel ret = new JPanel(new BorderLayout());
        ret.add(wrapped, BorderLayout.EAST);
        return ret;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        TextFieldWithBrowseButton settingsFileBrowser = new TextFieldWithBrowseButton(settingsFile, x -> reloadServersInBackground());
        TextFieldWithBrowseButton awsPathBrowser = new TextFieldWithBrowseButton(awsPath);
        ComponentWithBrowseButton<ComboBoxWithWidePopup> mavenServerIdWrapper =
                new ComponentWithBrowseButton<>(serverIdComboBox, x -> reloadServersInBackground());
        ComponentWithBrowseButton<ComboBoxWithWidePopup> profileWrapper =
                new ComponentWithBrowseButton<>(profileComboBox, x -> reloadProfilesInBackground());
        ComponentWithBrowseButton<ComboBoxWithWidePopup> configurations =
                new ComponentWithBrowseButton<>(configurationComboBox, x -> renameConfiguration());
        removeConfigurationsComponent = new ComponentWithBrowseButton<>(configurations, this::deleteConfiguration);
        ComponentWithBrowseButton<ComponentWithBrowseButton> configurationsWithAdd =
                new ComponentWithBrowseButton<>(removeConfigurationsComponent, this::createConfiguration);

        double labelsWeight = 2.0;

        GridBag gridbag = new GridBag()
                .setDefaultWeightX(labelsWeight * 5)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)
                .setDefaultInsets(JBUI.insets(0, 0, AbstractLayout.DEFAULT_VGAP, AbstractLayout.DEFAULT_HGAP));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(new TitledSeparator("Repository Configurations"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Configuration:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(configurationsWithAdd, gridbag.next().coverLine());
        centerPanel.add(getLabel("Enabled"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(enabledCheckbox, gridbag.next().coverLine());
        centerPanel.add(new TitledSeparator("Repository Info"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Domain:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(domain, gridbag.next().coverLine());
        centerPanel.add(getLabel("Domain owner:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(domainOwner, gridbag.next().coverLine());
        centerPanel.add(getLabel("Maven server id:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(mavenServerIdWrapper, gridbag.next().coverLine());

        centerPanel.add(serverWarningEmptyLabel, gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(serverWarningLabel, gridbag.next().coverLine());
        centerPanel.add(getLabel("AWS profile:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(profileWrapper, gridbag.next().coverLine());
        centerPanel.add(profileWarningEmptyLabel, gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(profileWarningLabel, gridbag.next().coverLine());
        centerPanel.add(getLabel("Region:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(regionComboBox, gridbag.next().coverLine());
        centerPanel.add(new TitledSeparator("Locations"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Maven settings file:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(settingsFileBrowser, gridbag.next().coverLine());
        centerPanel.add(getLabel("AWS cli path:"), gridbag.nextLine().next().weightx(labelsWeight));
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
        profileWrapper.setButtonIcon(AllIcons.Actions.Refresh);
        configurations.setButtonIcon(AllIcons.General.Settings);
        configurations.setToolTipText("Rename configuration");
        removeConfigurationsComponent.setButtonIcon(AllIcons.General.Remove);
        removeConfigurationsComponent.setButtonEnabled(false);
        configurations.setToolTipText("Delete configuration (with confirmation)");
        configurationsWithAdd.setButtonIcon(AllIcons.General.Add);
        configurations.setToolTipText("Create new configuration");

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

    private JBLabel getLabel(String text) {
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

    private void setSelectedRegion(String s) {
        if (s == null || !Configuration.getValidRegions().contains(s)) {
            regionComboBox.setSelectedItem(Configuration.DEFAULT_PROFILE_REGION);
        } else {
            regionComboBox.setSelectedItem(s);
        }
    }

    private static final Object LOADING = new Object() {
        @Override
        public String toString() {
            return "Loading ...";
        }
    };

}
