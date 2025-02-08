package net.coderazzi.aws_codeartifact_maven.state;

import net.coderazzi.aws_codeartifact_maven.utils.AWSProfileHandler;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.*;

final public class Configuration {
    public static final String DEFAULT_AWS_CLI_PATH = "aws";
    public static final String DEFAULT_PROFILE_REGION = "<default profile region>";
    private static final String DEFAULT_CONFIGURATION_NAME = "main";
    private static final int VERSION_2024NOV09 = 7;
    private static final String VALID_REGIONS = // 13 regions:
            // https://aws.amazon.com/codeartifact/faq/
            // https://www.aws-services.info/codeartifact.html
            "ap-northeast-1,ap-south-1,ap-southeast-1,ap-southeast-2," +
                    "eu-central-1,eu-north-1,eu-south-1,eu-west-1,eu-west-2,eu-west-3," +
                    "us-east-1,us-east-2,us-west-2";
    private static final TreeSet<String> validRegions= new TreeSet<>(Arrays.asList(VALID_REGIONS.split(",")));
    private final PersistentState state;

    public Configuration(){
        state = PersistentState.getInstance();
        ensureInitialization();
    }

    public static Set<String> getValidRegions() {
        return validRegions;
    }

    private void ensureInitialization() {
        // store profiles and maven server ids as sets, but manage them as treesets
        // (json for treeset not properly handled)
        state.allProfiles = state.allProfiles==null? new TreeSet<>() : new TreeSet<>(state.allProfiles);
        state.allMavenServerIds = state.allMavenServerIds==null? new TreeSet<>() : new TreeSet<>(state.allMavenServerIds);
        if (state.domains == null) state.domains = new HashMap<>();
        if (state.regions == null) state.regions = new HashMap<>();
        if (state.domainOwners == null) state.domainOwners = new HashMap<>();
        if (state.mavenSettingsFile == null) {
            String home = System.getProperty("user.home");
            if (home == null) {
                state.mavenSettingsFile = "";
            } else {
                state.mavenSettingsFile = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        if (state.awsPath == null) {
            state.awsPath = DEFAULT_AWS_CLI_PATH;
        }
        if (state.awsProfile == null || state.awsProfile.isEmpty()) {
            String envAwsProfile = System.getenv("AWS_PROFILE");
            if (envAwsProfile != null) {
                state.awsProfile = envAwsProfile.trim();
            }
            if (state.awsProfile == null || state.awsProfile.isEmpty()) {
                state.awsProfile = AWSProfileHandler.DEFAULT_PROFILE;
            }
        }
        if (state.awsConfigurations == null || state.awsConfigurations.isEmpty()) {
            // migrating from old versions, where state.configurations where not defined
            // or new installation
            state.awsConfigurations = new HashMap<>();
            if (state.allMavenServerIds.isEmpty()){
                AwsConfiguration conf = new AwsConfiguration();
                conf.enabled = true;
                state.awsConfigurations.put(DEFAULT_CONFIGURATION_NAME, conf);
                state.configuration = DEFAULT_CONFIGURATION_NAME;
            } else {
                for (String id : state.allMavenServerIds) {
                    AwsConfiguration conf = new AwsConfiguration();
                    conf.profile = state.awsProfile;
                    conf.mavenServerId = id;
                    conf.domain = state.domains.get(id);
                    conf.domainOwner = state.domainOwners.get(id);
                    conf.region = state.regions.get(state.mavenServerId);
                    state.awsConfigurations.put(id, conf);
                }
                if (state.awsConfigurations.containsKey(state.mavenServerId)) {
                    state.configuration = state.mavenServerId;
                } else {
                    state.configuration = state.awsConfigurations.keySet().iterator().next();
                }
                state.awsConfigurations.get(state.configuration).enabled = true;
            }
            // remove next fields from state.configuration
            state.mavenServerId = null;
            state.awsProfile = null;
            state.regions = null;
            state.domains = null;
            state.domainOwners = null;
        } else if (!state.awsConfigurations.containsKey(state.configuration)) {
            state.configuration = state.awsConfigurations.keySet().iterator().next();
        }
        state.version = VERSION_2024NOV09;
    }

    public AwsConfiguration getCurrentConfiguration() {
        return state.awsConfigurations.get(state.configuration);
    }

    public AwsConfiguration getConfiguration(String configurationName) {
        return state.awsConfigurations.get(configurationName);
    }

    public Set<String> getConfigurationNames() {
        return new TreeSet<>(state.awsConfigurations.keySet());
    }

    public String getConfigurationName() {
        return state.configuration;
    }

    public void setConfigurationName(String configuration){
        state.configuration = configuration;
    }

    public String getAWSPath() {
        return state.awsPath;
    }

    public void setAwsPath(@NotNull String awsPath) {
        state.awsPath = awsPath;
    }

    public String getAWSVaultPath() {
        return state.awsVaultPath;
    }

    public void setAwsVaultPath(@NotNull String awsVaultPath) {
        state.awsVaultPath = awsVaultPath;
    }

    public String getMavenServerSettingsFile() {
        return state.mavenSettingsFile;
    }

    public void setMavenSettingsFile(@NotNull String mavenSettingsFile) {
        state.mavenSettingsFile = mavenSettingsFile;
    }

    public Set<String> getProfileNames() {
        return Collections.unmodifiableSet(state.allProfiles);
    }

    public void setProfileNames(Set<String> allProfiles) {
        state.allProfiles.clear();
        state.allProfiles.addAll(allProfiles);
    }

    public boolean isValidProfileName(String profileName) {
        return state.allProfiles.contains(profileName);
    }

    public  Set<String> getDefinedMavenServerIds() {
        return Collections.unmodifiableSet(state.allMavenServerIds);
    }

    public void setDefinedMavenServerIds(Set<String> allMavenServerIds) {
        state.allMavenServerIds.clear();
        state.allMavenServerIds.addAll(allMavenServerIds);
    }

    public boolean isDefinedMavenServerId(String mavenServerId) {
        return state.allMavenServerIds.contains(mavenServerId);
    }

    public boolean hasMultipleConfigurations() {
        return state.awsConfigurations.size() > 1;
    }

    public boolean isGenerateForAll() {
        return state.generateForAll;
    }

    public void setGenerateForAll(boolean generateForAll) {
        state.generateForAll = generateForAll;
    }


    public void addConfiguration(String name) {
        AwsConfiguration current = getCurrentConfiguration();
        AwsConfiguration conf = new AwsConfiguration();
        conf.domain = current.domain;
        conf.domainOwner = current.domainOwner;
        conf.mavenServerId = null;
        conf.region = current.region;
        conf.profile = current.profile;
        conf.enabled = true;
        state.awsConfigurations.put(name, conf);
        state.configuration = name;
    }

    public boolean renameConfiguration(String newName) {
        if (newName.equals(state.configuration)) {
            return false;
        }
        state.awsConfigurations.put(newName, state.awsConfigurations.get(state.configuration));
        state.awsConfigurations.remove(state.configuration);
        state.configuration = newName;
        return true;
    }

    public void deleteConfiguration(){
        state.awsConfigurations.remove(state.configuration);
        state.configuration = state.awsConfigurations.keySet().iterator().next();
    }

}