package net.coderazzi.aws_codeartifact_maven;

import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

final public class InputDialogState {

    public static final String DEFAULT_AWS_CLI_PATH = "aws";

    private final PluginState state;
    private final TreeSet<String> allMavenServerIds;
    private final TreeSet<String> allProfiles;

    public static InputDialogState getInstance() {
        return new InputDialogState(PluginState.getInstance());
    }

    private InputDialogState(PluginState state) {
        this.state = state;
        // alas, Idea serializes TreeSets as Sets, and loads them as HashSets
        // so we just load the state, and convert the set to TreeSet
        allMavenServerIds = new TreeSet<>(state.allMavenServerIds);
        state.allMavenServerIds = allMavenServerIds;
        allProfiles = new TreeSet<>(state.allProfiles);
        state.allProfiles = allProfiles;
    }

    public void updateDomain(String domain) {
        state.domains.put(state.mavenServerId, domain);
    }

    public void updateDomainOwner(String domainOwner) {
        state.domainOwners.put(state.mavenServerId, domainOwner);
    }

    public void updateAwsPath(String aws) {
        state.awsPath = aws;
    }

    public void updateMavenServerId(String id) {
        state.mavenServerId = id;
    }

    public void updateMavenServerIds(Set<String> ids) {
        allMavenServerIds.clear();
        allMavenServerIds.addAll(ids);
        if (!ids.contains(state.mavenServerId) && !allMavenServerIds.isEmpty()) {
            state.mavenServerId = allMavenServerIds.first();
        }
    }

    public boolean updateMavenSettingsFile(String mavenSettingsFile) {
        if (mavenSettingsFile.equals(state.mavenSettingsFile)) {
            return false;
        }
        state.mavenSettingsFile = mavenSettingsFile;
        return true;
    }

    public String getDomain() {
        return getDomain("");
    }

    public String getDomain(String current) {
        String ret = state.domains.get(state.mavenServerId);
        return ret == null ? current : ret;
    }

    public String getDomainOwner() {
        return getDomainOwner("");
    }

    public String getDomainOwner(String current) {
        String ret = state.domainOwners.get(state.mavenServerId);
        return ret == null ? current : ret;
    }

    public String getMavenServerId() {
        return state.mavenServerId;
    }

    public SortedSet<String> getMavenServerIds() {
        return allMavenServerIds;
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

    public String getProfile() {
        String ret = state.awsProfile;
        if (!isValidProfile(ret)) {
            ret = AWSProfileHandler.DEFAULT_PROFILE;
            String awsProfile = System.getenv("AWS_PROFILE");
            if (awsProfile != null) {
                awsProfile = awsProfile.trim();
                if (isValidProfile(awsProfile)) {
                    ret = awsProfile;
                }
            }
        }
        return isValidProfile(ret) ? ret : null;
    }

    private boolean isValidProfile(String profile) {
        return profile != null && allProfiles.contains(profile);
    }

    public void setProfile(String profile) {
        if (state.allProfiles != null && state.allProfiles.contains(profile)) {
            state.awsProfile = profile;
        }
    }

    public Set<String> getProfiles() {
        return state.allProfiles;
    }

    public void setProfiles(Set<String> profiles) {
        allProfiles.clear();
        allProfiles.addAll(profiles);
        state.allProfiles = allProfiles;
    }

}