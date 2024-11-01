package net.coderazzi.aws_codeartifact_maven;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

final public class InputDialogState {

    public static final String DEFAULT_AWS_CLI_PATH = "aws";
    public static final String DEFAULT_PROFILE_REGION = "<default profile region>";
    private static final String VALID_REGIONS = // 13 regions:
            // https://aws.amazon.com/codeartifact/faq/
            // https://www.aws-services.info/codeartifact.html
            "ap-northeast-1,ap-south-1,ap-southeast-1,ap-southeast-2," +
            "eu-central-1,eu-north-1,eu-south-1,eu-west-1,eu-west-2,eu-west-3," +
            "us-east-1,us-east-2,us-west-2";

    private final PluginState state;
    private final TreeSet<String> validRegions = new TreeSet<>(Arrays.asList(VALID_REGIONS.split(",")));

    public static InputDialogState getInstance() {
        return new InputDialogState(PluginState.getInstance());
    }

    private InputDialogState(PluginState state) {
        this.state = state;
    }

    public void setDomain(String domain) {
        state.getCurrentConfiguration().domain = domain;
    }

    public void setRegion(String region) {
        state.getCurrentConfiguration().region = region;
    }

    public void setDomainOwner(String domainOwner) {
        state.getCurrentConfiguration().domainOwner = domainOwner;
    }

    public void setAwsPath(String aws) {
        state.awsPath = aws;
    }

    public void setMavenServerId(String id) {
        state.getCurrentConfiguration().mavenServerId = id;
    }

    public void setMavenServerIds(Set<String> ids) {
        state.allMavenServerIds.clear();
        state.allMavenServerIds.addAll(ids);
    }

    public boolean setMavenSettingsFile(String mavenSettingsFile) {
        if (mavenSettingsFile.equals(state.mavenSettingsFile)) {
            return false;
        }
        state.mavenSettingsFile = mavenSettingsFile;
        return true;
    }

    public String getDomain() {
        String ret = state.getCurrentConfiguration().domain;
        return ret == null ? "" : ret;
    }

    public String getRegion() {
        String ret = state.getCurrentConfiguration().region;
        return ret == null || (!ret.equals(DEFAULT_PROFILE_REGION) && !validRegions.contains(ret)) ?
                DEFAULT_PROFILE_REGION : ret;
    }

    public Set<String> getValidRegions() {
        return validRegions;
    }

    public String getDomainOwner() {
        String ret = state.getCurrentConfiguration().domainOwner;
        return ret == null ? "" : ret;
    }

    public String getMavenServerId() {
        return state.getCurrentConfiguration().mavenServerId;
    }

    public Set<String> getMavenServerIds() {
        return state.allMavenServerIds;
    }

    public String getAWSPath() {
        String ret = state.awsPath;
        return ret.trim().isEmpty() ? DEFAULT_AWS_CLI_PATH : ret;
    }

    public String getMavenServerSettingsFile() {
        String ret = state.mavenSettingsFile;
        if (ret.isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null) {
                ret = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        return ret;
    }

    public boolean isConfigurationEnabled() {
        return Boolean.TRUE.equals(state.getCurrentConfiguration().enabled);
    }

    public void setConfigurationEnabled(boolean enabled) {
        state.getCurrentConfiguration().enabled = enabled;
    }

    public String getProfile() {
        String ret = state.getCurrentConfiguration().awsProfile;
        return ret == null ? AWSProfileHandler.DEFAULT_PROFILE : ret;
    }

    public void setProfile(String profile) {
        state.getCurrentConfiguration().awsProfile = profile;
    }

    public Set<String> getProfiles() {
        return state.allProfiles;
    }

    public void setProfiles(Set<String> profiles) {
        state.allProfiles.clear();
        state.allProfiles.addAll(profiles);
    }

    public Set<String> getConfigurationNames() {
        return new TreeSet<>(state.configurations.keySet());
    }

    public boolean isMultipleGenerationEnabled() {
        System.out.println("Asked for isConfigurationEnabled for configuration " + state.configuration + " / " +
                state.getCurrentConfiguration().enabled + " / " +
                Boolean.TRUE.equals(state.getCurrentConfiguration().enabled)
        );
        return state.configurations.size() > 1 && state.configurations.values().stream().anyMatch(x->x.enabled);
    }

    /**
     * Returns the configuration name, or null if none has been defined
     */
    public String getCurrentConfiguration() {
        return state.configuration;
    }

    public void setCurrentConfiguration(String name) {
        state.configuration = name;
    }

    public void addConfiguration(String name) {
        PluginState.Configuration current = state.getCurrentConfiguration();
        PluginState.Configuration conf = new PluginState.Configuration();
        conf.domain = current.domain;
        conf.domainOwner = current.domainOwner;
        conf.mavenServerId = null;
        conf.region = current.region;
        conf.awsProfile = current.awsProfile;
        conf.enabled = true;
        state.configurations.put(name, conf);
        state.configuration = name;
    }

    public boolean renameConfiguration(String newName) {
        if (newName.equals(state.configuration)) {
            return false;
        }
        state.configurations.put(newName, state.configurations.get(state.configuration));
        state.configurations.remove(state.configuration);
        state.configuration = newName;
        return true;
    }

    public void deleteConfiguration(){
        state.configurations.remove(state.configuration);
        state.configuration = state.configurations.keySet().iterator().next();
    }

}