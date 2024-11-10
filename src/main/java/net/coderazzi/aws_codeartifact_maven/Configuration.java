package net.coderazzi.aws_codeartifact_maven;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class Configuration {
    public String mavenServerId;
    public String awsProfile;
    public String region;
    public String domain;
    public String domainOwner;
    public boolean enabled;
    private static final TreeSet<String> validRegions;

    public String getDomain() {
        return domain == null ? "" : domain;
    }

    public String getRegion() {
        return region == null || (!region.equals(PluginState.DEFAULT_PROFILE_REGION) && !validRegions.contains(region)) ?
                PluginState.DEFAULT_PROFILE_REGION : region;
    }

    public static Set<String> getValidRegions() {
        return validRegions;
    }

    public String getDomainOwner() {
        return domainOwner == null ? "" : domainOwner;
    }

    public String getProfile() {
        return awsProfile == null ? AWSProfileHandler.DEFAULT_PROFILE : awsProfile;
    }

    private static final String VALID_REGIONS = // 13 regions:
            // https://aws.amazon.com/codeartifact/faq/
            // https://www.aws-services.info/codeartifact.html
            "ap-northeast-1,ap-south-1,ap-southeast-1,ap-southeast-2," +
                    "eu-central-1,eu-north-1,eu-south-1,eu-west-1,eu-west-2,eu-west-3," +
                    "us-east-1,us-east-2,us-west-2";

    static {
        validRegions = new TreeSet<>(Arrays.asList(VALID_REGIONS.split(",")));
    }
}