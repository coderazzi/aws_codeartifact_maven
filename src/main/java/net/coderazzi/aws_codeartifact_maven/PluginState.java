package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@com.intellij.openapi.components.State(
        name = "aws_codeartifact_maven.state",
        storages = @Storage("aws_codeartifact_maven.xml"))//, roamingType = RoamingType.DISABLED))
final public class PluginState implements PersistentStateComponent<PluginState> {

    public static final int CURRENT_VERSION = 4;

    public static PluginState getInstance() {
        return ServiceManager.getService(PluginState.class).ensureInitialization();
    }

    public int version;
    public String mavenSettingsFile;
    public String awsPath;
    public String mavenServerId;
    public String awsProfile;
    public Set<String> allProfiles;
    public Set<String> allMavenServerIds;
    public Map<String, String> regions;      // mavenServerId -> region
    public Map<String, String> domains;      // mavenServerId -> domain
    public Map<String, String> domainOwners; // mavenServerId -> domainOwner

    public PluginState getState() {
        return this;
    }

    public void loadState(@NotNull PluginState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    private PluginState ensureInitialization() {
        if (allProfiles == null) allProfiles = new TreeSet<>();
        if (allMavenServerIds == null) allMavenServerIds = new TreeSet<>();
        if (domains == null) domains = new HashMap<>();
        if (regions == null) regions = new HashMap<>();
        if (domainOwners == null) domainOwners = new HashMap<>();
        if (version == 0) {
            // migrating from old PropertiesComponent persistence
            PropertiesComponent properties = PropertiesComponent.getInstance();
            mavenSettingsFile = getAndCleanPropertiesComponentProperty(properties, "mavenSettingsFile");
            awsPath = getAndCleanPropertiesComponentProperty(properties, "awsPath");
            mavenServerId = getAndCleanPropertiesComponentProperty(properties, "mavenServerId");
            if (!mavenServerId.isEmpty()) {
                // allMavenServerIds.add(mavenServerId); // do not load this, so that the maven settings file is read
                domains.put(mavenServerId, getAndCleanPropertiesComponentProperty(properties, "domain"));
                domainOwners.put(mavenServerId, getAndCleanPropertiesComponentProperty(properties, "domainOwner"));
            }
        }
        version = CURRENT_VERSION;
        return this;
    }

    private String getAndCleanPropertiesComponentProperty(PropertiesComponent properties, String name) {
        String key = String.format("net.coderazzi.aws_codeartifact_maven.%s", name);
        String ret = properties.getValue(key, "");
        properties.unsetValue(key);
        return ret;
    }

}