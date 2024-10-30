package net.coderazzi.aws_codeartifact_maven.settings;

import static net.coderazzi.aws_codeartifact_maven.settings.DefaultSettingsUtil.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "net.coderazzi.aws_codeartifact_maven.settings.AppSettings", storages = @Storage("AwsCodeartifactMavenPlugin.xml"))
public final class AppSettings implements PersistentStateComponent<AppSettings.State> {

    public static class State {
        private String mavenSettingsFile = "";
        private String awsPath = "";
        private String mavenServerId = "";
        private String awsProfile = "";
        private String region = "";
        private String domain = "";
        private String awsAccountId = "";
        private long refreshIntervalMinutes = 0;

        public String getMavenSettingsFile() {
            return mavenSettingsFile;
        }

        public void setMavenSettingsFile(String mavenSettingsFile) {
            if (mavenSettingsFile == null || mavenSettingsFile.isBlank()) {
                this.mavenSettingsFile = getDefaultMavenSettingsPath();
            } else {
                this.mavenSettingsFile = mavenSettingsFile;
            }
        }

        public String getAwsPath() {
            return awsPath;
        }

        public void setAwsPath(String awsPath) {
            if (awsPath == null || awsPath.isBlank()) {
                this.awsPath = getDefaultAwsPath();
            } else {
                this.awsPath = awsPath;
            }
        }

        public String getMavenServerId() {
            return mavenServerId;
        }

        public void setMavenServerId(String mavenServerId) {
            if (mavenServerId == null || mavenServerId.isBlank()) {
                this.mavenServerId = getDefaultMavenServerId(mavenSettingsFile);
            } else {
                this.mavenServerId = mavenServerId;
            }
        }

        public String getAwsProfile() {
            return awsProfile;
        }

        public void setAwsProfile(String awsProfile) {
            if (awsProfile == null || awsProfile.isBlank()) {
                this.awsProfile = getDefaultAwsProfile();
            } else {
                this.awsProfile = awsProfile;
            }
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getAwsAccountId() {
            return awsAccountId;
        }

        public void setAwsAccountId(String awsAccountId) {
            this.awsAccountId = awsAccountId;
        }

        public long getRefreshIntervalMinutes() {
            return refreshIntervalMinutes;
        }

        public void setRefreshIntervalMinutes(long refreshIntervalMinutes) {
            this.refreshIntervalMinutes = refreshIntervalMinutes;
        }
    }

    private State state = new State();

    public static AppSettings getInstance() {
        return ApplicationManager.getApplication().getService(AppSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
