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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.util.ui.JBUI.Borders.empty;

class InputDialog extends DialogWrapper {

    public static final String COMPONENT_TITLE = "CodeArtifact + Maven";
    private static final String MAVEN_SERVER_USERNAME = "aws";

    private static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private static String LIGHT_ICON = "META-INF/pluginIcon.svg";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final DefaultComboBoxModel<String> servers = new DefaultComboBoxModel<>();
    private final ComboBoxWithWidePopup<String> mavenServerId = new ComboBoxWithWidePopup<>(servers);
    private final JTextField mavenSettingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);
    private Thread loadingServersThread;
    private PluginState state;

    private final PropertiesComponent properties;

    public InputDialog() {
        super(true); // use current window as parent
        properties = PropertiesComponent.getInstance();
        state = PluginState.getInstance();
        init();
        setTitle("Generate AWS CodeArtifact Credentials");
        setAutoAdjustable(true);
        setOKButtonText("Generate credentials");
    }

    public PluginState getState() {
        return state;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        TextFieldWithBrowseButton mvnBrowser = new TextFieldWithBrowseButton(mavenSettingsFile, x -> reloadServers());
        TextFieldWithBrowseButton awsBrowser = new TextFieldWithBrowseButton(awsPath);
        ComponentWithBrowseButton<ComboBoxWithWidePopup<String>> mvnServer =
                new ComponentWithBrowseButton<>(mavenServerId, x-> reloadServers());

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
        centerPanel.add(mvnServer, gridbag.next().coverLine());
        centerPanel.add(new TitledSeparator("Locations"), gridbag.nextLine().coverLine());
        centerPanel.add(getLabel("Maven settings file:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(mvnBrowser, gridbag.next().coverLine());

        centerPanel.add(getLabel("AWS cli path:"), gridbag.nextLine().next().weightx(2.0));
        centerPanel.add(awsBrowser, gridbag.next().coverLine());

        mavenSettingsFile.setText(state.getMavenServerSettingsFile());
        mavenSettingsFile.addActionListener(x -> reloadServers()); // handle ENTER key
        awsPath.setText(state.getAWSPath());
        updateRepositoryInformation(true);

        mvnBrowser.addBrowseFolderListener("Maven Settings File", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        awsBrowser.addBrowseFolderListener("aws Executable Location", null, null,
                new FileChooserDescriptor(true, false, false, false, false, false));
        mvnServer.setButtonIcon(AllIcons.Actions.Refresh);

        checkGenerateCredentialsButtonState(awsPath);
        checkGenerateCredentialsButtonState(mavenSettingsFile);
        checkGenerateCredentialsButtonState(domainOwner);
        checkGenerateCredentialsButtonState(domain);
        checkGenerateCredentialsButtonState(mavenServerId);

        JPanel ret = new JPanel(new BorderLayout(24, 0));
        ret.add(centerPanel, BorderLayout.CENTER);
        ret.add(getIconPanel(), BorderLayout.WEST);

        return ret;
    }


    private void updateRepositoryInformation(boolean reloadServersIfNeeded){
        domain.setText(state.getDomain(domain.getText()));
        domainOwner.setText(state.getDomainOwner(domainOwner.getText()));
        servers.removeAllElements();

        Set<String> serverIds = state.getMavenServerIds();
        if (serverIds.isEmpty()) {
            if (reloadServersIfNeeded) {
                reloadServers();
            }
        } else {
            for (String each : serverIds) {
                servers.addElement(each);
            }
            servers.setSelectedItem(state.getMavenServerId());
        }
    }

    private void reloadServers(){
        final String filename = mavenSettingsFile.getText().trim();
        if (loadingServersThread==null || state.updateMavenSettingsFile(filename)) {
            servers.removeAllElements();
            if (!filename.isEmpty()) {
                servers.addElement("Loading server ids from maven file...");
                mavenServerId.setEnabled(false);
                loadingServersThread = new Thread(() -> loadingServersInBackground(filename));
                loadingServersThread.start();
            }
        }
    }

    private void loadingServersInBackground(String settingsFile){
        try {
            updateServersInForeground(
                    new MavenSettingsFileHandler(settingsFile).getServerIds(MAVEN_SERVER_USERNAME),
                    null
            );
        } catch (MavenSettingsFileHandler.GetServerIdsException ex){
            updateServersInForeground(new HashSet<>(), ex.getMessage());
        }
    }

    private void updateServersInForeground(Set<String> serverIds, String error){
        final Thread thread = Thread.currentThread();
        SwingUtilities.invokeLater(() -> {
            if (thread == loadingServersThread) {
                state.setMavenServerIds(serverIds);
                loadingServersThread = null;
                updateRepositoryInformation(false);
                if (error != null) {
                    Messages.showErrorDialog(mavenSettingsFile, error, COMPONENT_TITLE);
                }
            }
        });
    }

    private JComponent getIconPanel(){
        String resource = ColorUtil.isDark(getOwner().getBackground())? DARK_ICON : LIGHT_ICON;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)){
            return new JLabel(new ImageIcon(SVGLoader.load(is, 2.5f)));
        } catch (IOException ex){
            return new JLabel();
        }
    }


    private JComponent getLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(empty(0, 5, 2, 0));
        return label;
    }

    @Override
    protected void doOKAction() {
        try {
            state.update(
                    getGuiValue(mavenServerId),
                    getGuiValue(domain),
                    getGuiValue(domainOwner),
                    getGuiValue(mavenSettingsFile),
                    getGuiValue(awsPath)
                    );
            super.doOKAction();
        } catch (InvalidState ex) {
            ex.component.requestFocus();
        }
    }

    @Override
    public void doCancelAction() {
        loadingServersThread = null;
        super.doCancelAction();
    }

    private void updateGenerateCredentialsButtonState(){
        try{
            getGuiValue(mavenServerId);
            getGuiValue(domain);
            getGuiValue(domainOwner);
            getGuiValue(mavenSettingsFile);
            getGuiValue(awsPath);
            getButton(getOKAction()).setEnabled(true);
        } catch(InvalidState ex){
            getButton(getOKAction()).setEnabled(false);
        }
    }

    private void checkGenerateCredentialsButtonState(JTextField check){
        check.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateGenerateCredentialsButtonState();
            }
        });
    }

    private void checkGenerateCredentialsButtonState(ComboBoxWithWidePopup<String> check){
        check.addItemListener(x -> updateGenerateCredentialsButtonState());
    }

    private String getGuiValue(JTextField check) throws InvalidState {
        String ret = check.getText().trim();
        if (ret.isEmpty()) {
            throw new InvalidState(check);
        }
        return ret;
    }

    private String getGuiValue(ComboBoxWithWidePopup<String> check) throws InvalidState {
        String selection = (String) check.getSelectedItem();
        if (selection == null) {
            throw new InvalidState(check);
        }
        return selection;
    }

    static class InvalidState extends Exception {
        JComponent component;
        InvalidState(JComponent component){ this.component = component;}
    }

}