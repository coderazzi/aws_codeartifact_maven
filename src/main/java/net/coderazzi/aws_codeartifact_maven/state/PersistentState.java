package net.coderazzi.aws_codeartifact_maven.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

@com.intellij.openapi.components.State(
        name = "aws_codeartifact_maven.state",
        storages = @Storage("aws_codeartifact_maven.xml"))//, roamingType = RoamingType.DISABLED))
final class PersistentState implements PersistentStateComponent<PersistentState> {
    public int version;
    public String mavenSettingsFile;
    public String awsPath;
    public boolean generateForAll;
    public String configuration;

    public Set<String> allProfiles;
    public Set<String> allMavenServerIds;
    public Map<String, AwsConfiguration> awsConfigurations;

    //next fields are obsolete since version 7 (2024Nov09)
    public String mavenServerId;
    public String awsProfile;
    public Map<String, String> regions;
    public Map<String, String> domains;
    public Map<String, String> domainOwners;

    public PersistentState getState() {
        return this;
    }

    public void loadState(@NotNull PersistentState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static PersistentState getInstance() {
        return ApplicationManager.getApplication().getService(PersistentState.class);
    }

}
