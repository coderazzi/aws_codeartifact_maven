package net.coderazzi.aws_codeartifact_maven.settings;

import static java.util.Objects.requireNonNull;
import static net.coderazzi.aws_codeartifact_maven.settings.AppSettings.getInstance;

import com.intellij.openapi.options.Configurable;
import javax.swing.*;
import net.coderazzi.aws_codeartifact_maven.internal.SchedulerService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Provides controller functionality for application settings.
 */
final class AppSettingsConfigurable implements Configurable {

    private AppSettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "AWS CodeArtifact Maven Plugin";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return settingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        settingsComponent = new AppSettingsComponent();
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        var state = requireNonNull(getInstance().getState());
        return !settingsComponent.getRegionText().equals(state.getRegion()) ||
                !settingsComponent.getAwsAccountIdText().equals(state.getAwsAccountId()) ||
                !settingsComponent.getDomainText().equals(state.getDomain()) ||
                !settingsComponent.getAwsProfileText().equals(state.getAwsProfile()) ||
                !settingsComponent.getMavenServerIdText().equals(state.getMavenServerId()) ||
                !settingsComponent.getMavenSettingsFileText().equals(state.getMavenSettingsFile()) ||
                !settingsComponent.getAwsCliPathText().equals(state.getAwsPath()) ||
                settingsComponent.getRefreshInterval().minutes() != state.getRefreshIntervalMinutes();
    }

    @Override
    public void apply() {
        var state = requireNonNull(getInstance().getState());
        state.setRegion(settingsComponent.getRegionText());
        state.setAwsAccountId(settingsComponent.getAwsAccountIdText());
        state.setDomain(settingsComponent.getDomainText());
        state.setAwsProfile(settingsComponent.getAwsProfileText());
        state.setMavenServerId(settingsComponent.getMavenServerIdText());
        state.setMavenSettingsFile(settingsComponent.getMavenSettingsFileText());
        state.setAwsPath(settingsComponent.getAwsCliPathText());
        state.setRefreshIntervalMinutes(settingsComponent.getRefreshInterval().minutes());

        SchedulerService.getInstance().updateSchedule(state.getRefreshIntervalMinutes());
    }

    @Override
    public void reset() {
        var state = requireNonNull(getInstance().getState());
        settingsComponent.setRegionText(state.getRegion());
        settingsComponent.setAwsAccountIdText(state.getAwsAccountId());
        settingsComponent.setDomainText(state.getDomain());
        settingsComponent.setAwsProfileText(state.getAwsProfile());
        settingsComponent.setMavenServerIdText(state.getMavenServerId());
        settingsComponent.setMavenSettingsFileText(state.getMavenSettingsFile());
        settingsComponent.setAwsCliPathText(state.getAwsPath());
        settingsComponent.setRefreshInterval(RefreshOption.ofMinutes(state.getRefreshIntervalMinutes()));
    }

    @Override
    public void disposeUIResources() {
        settingsComponent = null;
    }
}
