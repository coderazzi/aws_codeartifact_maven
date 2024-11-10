package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.*;

@com.intellij.openapi.components.State(
        name = "aws_codeartifact_maven.state",
        storages = @Storage("aws_codeartifact_maven.xml"))//, roamingType = RoamingType.DISABLED))
final public class PluginState implements PersistentStateComponent<PluginState> {

    public static final int VERSION_20241109 = 7;
    public static final String DEFAULT_AWS_CLI_PATH = "aws";
    public static final String DEFAULT_CONFIGURATION_NAME = "main";
    public static final String DEFAULT_PROFILE_REGION = "<default profile region>";
    private static final String VALID_REGIONS = // 13 regions:
            // https://aws.amazon.com/codeartifact/faq/
            // https://www.aws-services.info/codeartifact.html
            "ap-northeast-1,ap-south-1,ap-southeast-1,ap-southeast-2," +
                    "eu-central-1,eu-north-1,eu-south-1,eu-west-1,eu-west-2,eu-west-3," +
                    "us-east-1,us-east-2,us-west-2";
    private static final TreeSet<String> validRegions =
            new TreeSet<>(Arrays.asList(VALID_REGIONS.split(",")));

    public static class Configuration {
        public String mavenServerId;
        public String awsProfile;
        public String region;
        public String domain;
        public String domainOwner;
        public boolean enabled;
        public String getDomain() {
            return domain == null ? "" : domain;
        }
        public String getRegion() {
            return region == null || (!region.equals(DEFAULT_PROFILE_REGION) && !validRegions.contains(region)) ?
                    DEFAULT_PROFILE_REGION : region;
        }
        public String getDomainOwner() {
            return domainOwner == null ? "" : domainOwner;
        }
        public String getProfile() {
            return awsProfile == null ? AWSProfileHandler.DEFAULT_PROFILE : awsProfile;
        }
    }

    public static PluginState getInstance() {
        return ApplicationManager.getApplication()
                .getService(PluginState.class).ensureInitialization();
    }

    public int version;
    public String mavenSettingsFile;
    public String awsPath;
    public boolean generateForAll;

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
            // migrating from old PropertiesComponent persistence. This is really old, deprecate it??
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
        if (configurations == null || configurations.isEmpty()) {
            // migrating from old versions, where configurations where not defined
            // or new installation
            configurations = new HashMap<>();
            if (allMavenServerIds.isEmpty()){
                Configuration conf = new Configuration();
                conf.enabled = true;
                configurations.put(DEFAULT_CONFIGURATION_NAME, conf);
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
                configurations.get(configuration).enabled = true;
            }
            // remove next fields from configuration
            mavenServerId=null;
            awsProfile=null;
            regions=null;      // mavenServerId -> region
            domains = null;      // mavenServerId -> domain
            domainOwners = null;
        } else if (!configurations.containsKey(configuration)) {
            configuration = configurations.keySet().iterator().next();
        }
        version = VERSION_20241109;
        return this;
    }

    public Configuration getCurrentConfiguration() {
        return configurations.get(configuration);
    }

    public Set<String> getConfigurationNames() {
        return new TreeSet<>(configurations.keySet());
    }

    public boolean isMultipleGenerationEnabled() {
        return configurations.size() > 1 && configurations.values().stream().anyMatch(x->x.enabled);
    }

    public Set<String> getValidRegions() {
        return validRegions;
    }

    public String getAWSPath() {
        String ret = awsPath;
        return ret.trim().isEmpty() ? DEFAULT_AWS_CLI_PATH : ret;
    }

    public String getMavenServerSettingsFile() {
        String ret = mavenSettingsFile;
        if (ret.isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null) {
                ret = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        return ret;
    }

    public void addConfiguration(String name) {
        PluginState.Configuration current = getCurrentConfiguration();
        PluginState.Configuration conf = new PluginState.Configuration();
        conf.domain = current.domain;
        conf.domainOwner = current.domainOwner;
        conf.mavenServerId = null;
        conf.region = current.region;
        conf.awsProfile = current.awsProfile;
        conf.enabled = true;
        configurations.put(name, conf);
        configuration = name;
    }

    public boolean renameConfiguration(String newName) {
        if (newName.equals(configuration)) {
            return false;
        }
        configurations.put(newName, configurations.get(configuration));
        configurations.remove(configuration);
        configuration = newName;
        return true;
    }

    public void deleteConfiguration(){
        configurations.remove(configuration);
        configuration = configurations.keySet().iterator().next();
    }


    private String getAndCleanPropertiesComponentProperty(PropertiesComponent properties, String name) {
        String key = String.format("net.coderazzi.aws_codeartifact_maven.%s", name);
        String ret = properties.getValue(key, "");
        properties.unsetValue(key);
        return ret;
    }

}