package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
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

    public static final int CURRENT_VERSION = 6;
    public static final String DEFAULT_CONFIGURATION_NAME = "main";

    public static class Configuration {
        public String mavenServerId;
        public String awsProfile;
        public String region;
        public String domain;
        public String domainOwner;
    }

    public static PluginState getInstance() {
        return ApplicationManager.getApplication()
                .getService(PluginState.class).ensureInitialization();
    }

    public int version;
    public String mavenSettingsFile;
    public String awsPath;

    public Set<String> allProfiles;
    public Set<String> allMavenServerIds;
    public Map<String, Configuration> configurations;
    public String configuration;

    //next fields are obsolete
    public String mavenServerId;
    public String awsProfile;
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
        // store profiles and maven server ids as sets, but manage them as treesets
        // (json for treeset not properly handled)
        allProfiles = allProfiles==null? new TreeSet<>() : new TreeSet<>(allProfiles);
        allMavenServerIds = allMavenServerIds==null? new TreeSet<>() : new TreeSet<>(allMavenServerIds);
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
        if (awsProfile == null || awsProfile.isEmpty()) {
            String envAwsProfile = System.getenv("AWS_PROFILE");
            if (envAwsProfile != null) {
                awsProfile = envAwsProfile.trim();
            }
            if (awsProfile == null || awsProfile.isEmpty()) {
                awsProfile = AWSProfileHandler.DEFAULT_PROFILE;
            }
        }
        version = CURRENT_VERSION;
        if (configurations == null || configurations.isEmpty()) {
            configurations = new HashMap<>();
            if (allMavenServerIds.isEmpty()){
                configurations.put(DEFAULT_CONFIGURATION_NAME, new Configuration());
                configuration = DEFAULT_CONFIGURATION_NAME;
            } else {
                for (String id : allMavenServerIds) {
                    Configuration conf = new Configuration();
                    conf.awsProfile = awsProfile;
                    conf.mavenServerId = id;
                    conf.domain = domains.get(id);
                    conf.domainOwner = domainOwners.get(id);
                    conf.region = regions.get(mavenServerId);
                    configurations.put(id, conf);
                }
                if (configurations.containsKey(mavenServerId)) {
                    configuration = mavenServerId;
                } else {
                    configuration = configurations.keySet().iterator().next();
                }
            }
            // remove next fields from configuration
            mavenServerId=null;
            awsProfile=null;
            regions=null;      // mavenServerId -> region
            domains = null;      // mavenServerId -> domain
            domainOwners = null;
        } else if (configuration==null) {
            configuration = configurations.keySet().iterator().next();
        }
        return this;
    }

    public Configuration getCurrentConfiguration() {
        return configurations.get(configuration);
    }

    private String getAndCleanPropertiesComponentProperty(PropertiesComponent properties, String name) {
        String key = String.format("net.coderazzi.aws_codeartifact_maven.%s", name);
        String ret = properties.getValue(key, "");
        properties.unsetValue(key);
        return ret;
    }

}