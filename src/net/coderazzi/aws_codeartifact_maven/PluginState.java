package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.*;

@Service
@State(name = "aws_codeartifact_maven.state", storages = @Storage(value="aws_codeartifact_maven.xml"))//, roamingType = RoamingType.DISABLED))
final public class PluginState implements PersistentStateComponent<PluginState> {

    public static final String DEFAULT_AWS_CLI_PATH = "aws";
    public static final int CURRENT_VERSION=2;

    public static PluginState getInstance() {
        return ServiceManager.getService(PluginState.class).ensureInitialization();
    }

    @Property private int version;
    @Property private String mavenSettingsFile;
    @Property private String awsPath;
    @Property private String mavenServerId;
    @Property private TreeSet<String> allMavenServerIds;
    @Property private Map<String, String> domains;      // mavenServerId -> domain
    @Property private Map<String, String> domainOwners; // mavenServerId -> domainOwner

    public void update(String mavenServerId, String domain, String domainOwner, String mavenSettingsFile, String awsPath) {
        this.mavenServerId = mavenServerId;
        this.domains.put(this.mavenServerId, domain);
        this.domainOwners.put(this.mavenServerId, domainOwner);
        this.mavenSettingsFile = mavenSettingsFile;
        this.awsPath=awsPath;
    }

    public void setMavenServerIds(Set<String> ids){
        allMavenServerIds.clear();
        allMavenServerIds.addAll(ids);
    }

    public boolean updateMavenSettingsFile(String mavenSettingsFile){
        if (mavenSettingsFile.equals(this.mavenSettingsFile)) {
            return false;
        }
        this.mavenSettingsFile = mavenSettingsFile;
        return true;
    }

    public String getDomain(){return getDomain("");}

    public String getDomain(String current){
        String ret = domains.get(mavenServerId);
        return ret==null? current : ret;
    }

    public String getDomainOwner(){
        return getDomainOwner("");
    }

    public String getDomainOwner(String current){
        String ret = domainOwners.get(mavenServerId);
        return ret==null? current : ret;
    }

    public String getMavenServerId(){
        return mavenServerId;
    }

    public SortedSet<String> getMavenServerIds(){
        return this.allMavenServerIds;
    }

    public String getAWSPath(){
        String ret =  awsPath;
        return ret.trim().isEmpty()? DEFAULT_AWS_CLI_PATH : ret;
    }

    public String getMavenServerSettingsFile(){
        String ret = mavenSettingsFile;
        if (ret.isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null){
                ret = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        return ret;
    }

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