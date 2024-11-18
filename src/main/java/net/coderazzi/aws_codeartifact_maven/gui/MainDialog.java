package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
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
import net.coderazzi.aws_codeartifact_maven.state.AwsConfiguration;
import net.coderazzi.aws_codeartifact_maven.utils.AWSProfileHandler;
import net.coderazzi.aws_codeartifact_maven.utils.MavenSettingsFileHandler;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.util.ui.JBUI.Borders.empty;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MainDialog extends DialogWrapper {

    public static final String COMPONENT_TITLE = "CodeArtifact + Maven";
    private static final String MAVEN_SERVER_USERNAME = "aws";

    private final static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private final static String LIGHT_ICON = "META-INF/pluginIcon.svg";
    public static final int LOAD_MESSAGE_SUCCESS_TIMEOUT_MS = 1500;
    public static final Color LIGHT_SEPARATOR_COLOR = Color.LIGHT_GRAY;

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

    private final JBLabel serverWarningLabel, serverWarningEmptyLabel;
    private final JBLabel profileWarningLabel, profileWarningEmptyLabel;

    private final JTextField settingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);

    private final JBCheckBox generateAllCheckBox = new JBCheckBox("Generate Tokens for all configurations");
    private final JBCheckBox enabledCheckbox = new JBCheckBox();

    private boolean loadingProfiles;
    private Thread loadingServersThread;
    private final Project project;
    private final Configuration state = new Configuration();

    private ComponentWithBrowseButton<ComponentWithBrowseButton> removeConfigurationsComponent;

    public MainDialog(Project project) {
        super(project, true); // use current window as parent
        this.project = project;
        serverWarningLabel = createLabel("invalid server id, not found in settings file");
        serverWarningEmptyLabel = createLabel("");
        serverWarningLabel.setIcon(AllIcons.General.Error);
        serverWarningLabel.setVisible(false);
        serverWarningEmptyLabel.setVisible(false);
        profileWarningLabel = createLabel("invalid profile");
        profileWarningEmptyLabel = createLabel("");
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
        if (this.getOKAction().isEnabled() && new GenerationDialog(project, state).showAndGet()) {
            super.doOKAction();
        }
    }

    /**
     * Called whenever the user changes the maven server id
     */
    private void handleMavenServerIdChange() {
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
    private void handleProfileChange() {
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
    private void handleRegionChange() {
        Object s = regionComboBox.getSelectedItem();
        if (s != null) {
            state.getCurrentConfiguration().region = s instanceof String ? (String) s : "";
        }
    }

    private void handleConfigurationUpdate(){
        Object s = configurationComboBox.getSelectedItem();
        if (s != null){
            state.setConfigurationName(s.toString());
            AwsConfiguration current = state.getCurrentConfiguration();
            enabledCheckbox.setSelected(current.enabled);
            domain.setText(current.domain);
            domainOwner.setText(current.domainOwner);
            setSelectedRegion(current.region);
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
        generateAllCheckBox.setEnabled(state.hasMultipleConfigurations());
        removeConfigurationsComponent.setButtonEnabled(state.hasMultipleConfigurations());
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
                        if (error == null) {
                            setTooltipOnCombobox(serverIdsModel,
                                    String.format("Loaded %d server id%s", ids.size(), ids.size()>1 ? "s" : ""),
                                    loadingServersThread);
                        }
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
        if (!loadingProfiles) {
            loadingProfiles = true;
            profileComboBox.setEnabled(false);
            profileModel.removeAllElements();
            profileModel.addElement(LOADING);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                Set<String> profiles;
                String error = null;
                try {
                    profiles = AWSProfileHandler.getProfiles();
                    setTooltipOnCombobox(profileModel,
                            String.format("Loaded %d profile%s", profiles.size(), profiles.size()>1 ? "s" : ""),
                            null);
                } catch (AWSProfileHandler.GetProfilesException ex) {
                    profiles = AWSProfileHandler.getDefaultProfiles();
                    error = ex.getMessage();
                }
                updateProfilesInForeground(profiles, error);
            });
        }
    }

    /**
     * Displays the information for the aws profiles, once loaded in the background
     */
    private void updateProfilesInForeground(Set<String> profiles, String error) {
        //Cannot use here ApplicationManager.getApplication().invokeLater
        SwingUtilities.invokeLater(() -> {
            loadingProfiles = false;
            state.setProfileNames(profiles);
            profileComboBox.setEnabled(true);
            profileComboBox.requestFocus();
            showProfileInformation();
            if (error != null) {
                Messages.showErrorDialog(settingsFile, error, COMPONENT_TITLE);
            }
        });
    }

    private void setTooltipOnCombobox(DefaultComboBoxModel model, String message, Thread checkThread) {
        final Thread thread = Thread.currentThread();
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (checkThread == null || checkThread == thread) {
                    model.removeAllElements();
                    model.addElement(getTooltipObject(message));
                }
            });
            Thread.sleep(LOAD_MESSAGE_SUCCESS_TIMEOUT_MS);
        } catch(InterruptedException | InvocationTargetException ex) {
            // nothing to do
        }
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
            ok.setEnabled(
                    checkNonEmpty(awsPath)
                    && checkNonEmpty(settingsFile)
                    && (  (
                            generateAllCheckBox.isEnabled()
                            && generateAllCheckBox.isSelected())
                       || (
                            checkNonEmpty(domain)
                            && checkNonEmpty(domainOwner)
                            && checkHasSelection(serverIdComboBox)
                            && checkHasSelection(profileComboBox)
                            && !serverWarningLabel.isVisible()
                            && !profileWarningLabel.isVisible())));
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

    private void createConfiguration() {
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

    private void deleteConfiguration() {
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
        handleComboBoxChange(serverIdComboBox, this::handleMavenServerIdChange);
        handleComboBoxChange(profileComboBox, this::handleProfileChange);
        handleComboBoxChange(regionComboBox, this::handleRegionChange);
        handleComboBoxChange(configurationComboBox, this::handleConfigurationUpdate);
        enabledCheckbox.addItemListener(x->handleEnableConfigurationChange());
        generateAllCheckBox.addItemListener(x->handleGenerateAllChange());
        showConfigurationInformation(true);
    }

    private void handleGenerateAllChange() {
        state.setGenerateForAll(generateAllCheckBox.isSelected());
        updateGenerationButtonState();
    }

    private void handleEnableConfigurationChange() {
        state.getCurrentConfiguration().enabled = enabledCheckbox.isSelected();
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
        removeConfigurationsComponent = new ComponentWithBrowseButton<>(configurations, x -> deleteConfiguration());
        ComponentWithBrowseButton<ComponentWithBrowseButton> configurationsWithAdd =
                new ComponentWithBrowseButton<>(removeConfigurationsComponent, x -> createConfiguration());

        double labelsWeight = 2.0;

        GridBag gridbag = new GridBag()
                .setDefaultWeightX(labelsWeight * 5)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)
                .setDefaultInsets(JBUI.insets(0, 0, AbstractLayout.DEFAULT_VGAP, AbstractLayout.DEFAULT_HGAP));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(createTitledSeparator("Repository Configurations"), gridbag.nextLine().coverLine());
        centerPanel.add(createLabel("Configuration:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(configurationsWithAdd, gridbag.next().coverLine());
        centerPanel.add(createLabel("Enabled"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(enabledCheckbox, gridbag.next().coverLine());
        centerPanel.add(createTitledSeparator("Repository Info"), gridbag.nextLine().coverLine());
        centerPanel.add(createLabel("Domain:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(domain, gridbag.next().coverLine());
        centerPanel.add(createLabel("Domain owner:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(domainOwner, gridbag.next().coverLine());
        centerPanel.add(createLabel("Maven server id:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(mavenServerIdWrapper, gridbag.next().coverLine());

        centerPanel.add(serverWarningEmptyLabel, gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(serverWarningLabel, gridbag.next().coverLine());
        centerPanel.add(createLabel("AWS profile:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(profileWrapper, gridbag.next().coverLine());
        centerPanel.add(profileWarningEmptyLabel, gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(profileWarningLabel, gridbag.next().coverLine());
        centerPanel.add(createLabel("Region:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(regionComboBox, gridbag.next().coverLine());
        centerPanel.add(createTitledSeparator("Locations"), gridbag.nextLine().coverLine());
        centerPanel.add(createLabel("Maven settings file:"), gridbag.nextLine().next().weightx(labelsWeight));
        centerPanel.add(settingsFileBrowser, gridbag.next().coverLine());
        centerPanel.add(createLabel("AWS cli path:"), gridbag.nextLine().next().weightx(labelsWeight));
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
        JLabel icon = new JLabel();
        try {
            String resource = ColorUtil.isDark(getOwner().getBackground()) ? DARK_ICON : LIGHT_ICON;
            URL url = getClass().getClassLoader().getResource(resource);
            if (url != null) {
                icon.setIcon(IconLoader.findIcon(url));
            }
        } catch (Exception ex){
            // nothing to do here, just a missing icon
        }
        return icon;
    }

    private JBLabel createLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(empty(0, 5, 2, 0));
        return label;
    }

    private TitledSeparator createTitledSeparator(String text) {
        TitledSeparator ret = new TitledSeparator(text);
//        if (!ColorUtil.isDark(getOwner().getBackground())) {
//            ret.setBackground(LIGHT_SEPARATOR_COLOR);
//        }
//        ret.setBackground(LIGHT_SEPARATOR_COLOR);
        ret.getSeparator().setForeground(LIGHT_SEPARATOR_COLOR);
        return ret;
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

    private static Object getTooltipObject(final String message){
        return new Object() {
            @Override
            public String toString() {
                return message;
            }
        };
    }
    private static final Object LOADING = getTooltipObject("Loading ...");
}
