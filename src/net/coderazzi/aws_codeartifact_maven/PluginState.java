package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service
@com.intellij.openapi.components.State(name = "aws_codeartifact_maven.state", storages = @Storage("aws_codeartifact_maven.xml"))//, roamingType = RoamingType.DISABLED))
final public class PluginState implements PersistentStateComponent<PluginState> {

    public static final int CURRENT_VERSION=2;

    public static PluginState getInstance() {
        return ServiceManager.getService(PluginState.class).ensureInitialization();
    }

    public int version;
    public String mavenSettingsFile;
    public String awsPath;
    public String mavenServerId;
    public Set<String> allMavenServerIds;
    public Map<String, String> domains;      // mavenServerId -> domain
    public Map<String, String> domainOwners; // mavenServerId -> domainOwner

    public PluginState getState() {
        return this;
    }

    public void loadState(@NotNull PluginState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    private PluginState ensureInitialization(){
        if (version==0) {
            // migrating from old PropertiesComponent persistence
            PropertiesComponent properties = PropertiesComponent.getInstance();
            domains = new HashMap<>();
            domainOwners = new HashMap<>();
            allMavenServerIds = new TreeSet<>();
            mavenSettingsFile = getAndCleanPropertiesComponentProperty(properties, "mavenSettingsFile");
            awsPath = getAndCleanPropertiesComponentProperty(properties, "awsPath");
            mavenServerId = getAndCleanPropertiesComponentProperty(properties, "mavenServerId");
            version = CURRENT_VERSION;
            if (!mavenServerId.isEmpty()) {
                // allMavenServerIds.add(mavenServerId); // do not load this, so that the maven settings file is read
                domains.put(mavenServerId, getAndCleanPropertiesComponentProperty(properties, "domain"));
                domainOwners.put(mavenServerId, getAndCleanPropertiesComponentProperty(properties, "domainOwner"));
            }
        }
        return this;
    }

    private String getAndCleanPropertiesComponentProperty(PropertiesComponent properties, String name){
        String key = String.format("net.coderazzi.aws_codeartifact_maven.%s", name);
        String ret = properties.getValue(key, "");
        properties.unsetValue(key);
        return ret;
    }

}