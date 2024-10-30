package net.coderazzi.aws_codeartifact_maven.settings;

import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import java.awt.*;
import javax.swing.*;
import net.coderazzi.aws_codeartifact_maven.internal.CredentialsUpdater;
import org.jetbrains.annotations.NotNull;

public class AppSettingsComponent {
    private final JPanel mainPanel;
    private final JBTextField regionText = new JBTextField();
    private final JBTextField awsAccountIdText = new JBTextField();
    private final JBTextField domainText = new JBTextField();
    private final JBTextField mavenServerIdText = new JBTextField();
    private final JBTextField awsProfileText = new JBTextField();
    private final JBTextField mavenSettingsFileText = new JBTextField();
    private final JBTextField awsCliPathText = new JBTextField();
    private final DefaultComboBoxModel<RefreshOption> automaticRefreshIntervalMinutesModel = new DefaultComboBoxModel<>();
    private final ComboBoxWithWidePopup<RefreshOption> refreshIntervalCombo = new ComboBoxWithWidePopup<>(automaticRefreshIntervalMinutesModel);
    private final JButton testButton = new JButton("Execute & Test");
    private final JTextPane testOutput = new JTextPane();

    public AppSettingsComponent() {
        var formPanel = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("AWS CodeArtifact"))
                .addLabeledComponent(new JBLabel("AWS Region:"), regionText, 1, false)
                .addLabeledComponent(new JBLabel("AWS Account ID:"), awsAccountIdText, 1, false)
                .addLabeledComponent(new JBLabel("AWS CodeArtifact Domain:"), domainText, 1, false)
                .addLabeledComponent(new JBLabel("AWS Profile:"), awsProfileText, 1, false)
                .addComponent(new TitledSeparator("Maven"))
                .addLabeledComponent(new JBLabel("Maven Server ID:"), mavenServerIdText, 1, false)
                .addComponent(new TitledSeparator("Automatic Refresh"))
                .addLabeledComponent(new JBLabel("Refresh Interval:"), refreshIntervalCombo, 1, false)
                .addComponent(new TitledSeparator("Locations"))
                .addLabeledComponent(new JBLabel("Maven Settings File:"), mavenSettingsFileText, 1, false)
                .addLabeledComponent(new JBLabel("AWS CLI Path:"), awsCliPathText, 1, false)
                .addSeparator()
                .addComponent(testButton)
                .addComponent(testOutput)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        mainPanel = new JPanel(new BorderLayout(24, 0));
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(getIconPanel(), BorderLayout.EAST);

        setupTestButton();
        initCombos();
    }

    private void setupTestButton() {
        testButton.addActionListener(event -> {
            testOutput.setText("Testing...");

            SwingUtilities.invokeLater(() -> {
                var state = getLocalState();
                var result = CredentialsUpdater.runCredentialUpdateTask(state);
                var output = result.output() != null ? ": %s".formatted(result.output()) : "";
                var outcome = result.success() ? "succeeded" : "failed";
                testOutput.setText("Operation %s%s".formatted(outcome, output));
            });
        });
    }

    private AppSettings.@NotNull State getLocalState() {
        var state = new AppSettings.State();
        state.setRegion(getRegionText());
        state.setAwsAccountId(getAwsAccountIdText());
        state.setDomain(getDomainText());
        state.setAwsProfile(getAwsProfileText());
        state.setMavenServerId(getMavenServerIdText());
        state.setMavenSettingsFile(getMavenSettingsFileText());
        state.setAwsPath(getAwsCliPathText());
        state.setRefreshIntervalMinutes(getRefreshInterval().minutes());
        return state;
    }

    private void initCombos() {
        RefreshOption.REFRESH_OPTIONS.forEach(automaticRefreshIntervalMinutesModel::addElement);
    }

    private JComponent getIconPanel() {
        var label = new JLabel();
        try {
            var url = getClass().getClassLoader().getResource("META-INF/pluginIcon_dark.svg");
            if (url != null) {
                label.setIcon(IconLoader.findIcon(url));
            }
        } catch (Exception ex) {
            // nothing to do here, just a missing icon
        }
        return label;
    }


    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return regionText;
    }

    @NotNull
    public String getRegionText() {
        return regionText.getText();
    }

    public void setRegionText(@NotNull String newText) {
        regionText.setText(newText);
    }

    @NotNull
    public String getAwsAccountIdText() {
        return awsAccountIdText.getText();
    }

    public void setAwsAccountIdText(@NotNull String newText) {
        awsAccountIdText.setText(newText);
    }

    @NotNull
    public String getDomainText() {
        return domainText.getText();
    }

    public void setDomainText(@NotNull String newText) {
        domainText.setText(newText);
    }

    @NotNull
    public String getAwsProfileText() {
        return awsProfileText.getText();
    }

    public void setAwsProfileText(@NotNull String newText) {
        awsProfileText.setText(newText);
    }

    @NotNull
    public String getMavenServerIdText() {
        return mavenServerIdText.getText();
    }

    public void setMavenServerIdText(@NotNull String newText) {
        mavenServerIdText.setText(newText);
    }

    @NotNull
    public String getMavenSettingsFileText() {
        return mavenSettingsFileText.getText();
    }

    public void setMavenSettingsFileText(@NotNull String newText) {
        mavenSettingsFileText.setText(newText);
    }

    @NotNull
    public String getAwsCliPathText() {
        return awsCliPathText.getText();
    }

    public void setAwsCliPathText(@NotNull String newText) {
        awsCliPathText.setText(newText);
    }

    @NotNull
    public RefreshOption getRefreshInterval() {
        if (refreshIntervalCombo.getSelectedItem() == null) {
            return RefreshOption.DISABLED;
        }
        return (RefreshOption) refreshIntervalCombo.getSelectedItem();
    }

    public void setRefreshInterval(@NotNull RefreshOption newOption) {
        refreshIntervalCombo.setSelectedItem(newOption);
    }
}
