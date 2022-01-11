package net.coderazzi.aws_codeartifact_maven;

import java.nio.file.Paths;
import java.util.*;

final public class InputDialogState {

    public static final String DEFAULT_AWS_CLI_PATH = "aws";

    private PluginState state;
    private TreeSet<String> allMavenServerIds;
    private TreeSet<String> allProfiles = new TreeSet<>();
    private boolean loadProfiles;

    public static InputDialogState getInstance() {
        return new InputDialogState(PluginState.getInstance());
    }

    private InputDialogState(PluginState state){
        this.state = state;
        // alas, Idea serializes TreeSets as Sets, and loads them as HashSets
        // so we just load the state, and convert the set to TreeSet
        allMavenServerIds = new TreeSet<>(state.allMavenServerIds);
        state.allMavenServerIds = allMavenServerIds;
        if (state.allProfiles == null) {
            loadProfiles = true;
        } else {
            allProfiles.addAll(state.allProfiles);
            state.allProfiles = allProfiles;
        }
    }

    public boolean shouldLoadProfiles(){
        return loadProfiles;
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

    public void updateMavenServerId(String id){
        state.mavenServerId = id;
    }

    public void updateMavenServerIds(Set<String> ids){
        allMavenServerIds.clear();
        allMavenServerIds.addAll(ids);
        if (!ids.contains(state.mavenServerId) && !allMavenServerIds.isEmpty()) {
            state.mavenServerId = allMavenServerIds.first();
        }
    }

    public boolean updateMavenSettingsFile(String mavenSettingsFile){
        if (mavenSettingsFile.equals(state.mavenSettingsFile)) {
            return false;
        }
        state.mavenSettingsFile = mavenSettingsFile;
        return true;
    }

    public String getDomain(){return getDomain("");}

    public String getDomain(String current){
        String ret = state.domains.get(state.mavenServerId);
        return ret==null? current : ret;
    }

    public String getDomainOwner(){
        return getDomainOwner("");
    }

    public String getDomainOwner(String current){
        String ret = state.domainOwners.get(state.mavenServerId);
        return ret==null? current : ret;
    }

    public String getMavenServerId(){
        return state.mavenServerId;
    }

    public SortedSet<String> getMavenServerIds(){
        return allMavenServerIds;
    }

    public String getAWSPath(){
        String ret =  state.awsPath;
        return ret.trim().isEmpty()? DEFAULT_AWS_CLI_PATH : ret;
    }

    public String getMavenServerSettingsFile(){
        String ret = state.mavenSettingsFile;
        if (ret.isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null){
                ret = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        return ret;
    }

    public String getAWSProfile(){
        String ret = state.awsProfile;
        if (!validProfile(ret)) {
            ret = AWSProfileHandler.DEFAULT_PROFILE;
            String awsProfile = System.getenv("AWS_PROFILE");
            if (awsProfile != null) {
                awsProfile = awsProfile.trim();
                if (validProfile(awsProfile)) {
                    ret = awsProfile;
                }
            }
        }
        return validProfile(ret)? ret : null;
    }

    private boolean validProfile(String profile){
        return profile!=null && allProfiles.contains(profile);
    }

    public void setAWSProfile(String profile){
        if (state.allProfiles!=null && state.allProfiles.contains(profile)) {
            state.awsProfile = profile;
        }
    }

    public Set<String> getAWSProfiles(){
        return state.allProfiles;
    }

    public Set<String> updateAWSProfiles(Set<String> profiles){
        allProfiles.clear();
        allProfiles.addAll(profiles);
        state.allProfiles = allProfiles;
        loadProfiles = false;
        return allProfiles;
    }

}