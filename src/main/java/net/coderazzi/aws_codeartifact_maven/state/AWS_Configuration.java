package net.coderazzi.aws_codeartifact_maven.state;

import net.coderazzi.aws_codeartifact_maven.AWSProfileHandler;

public class AWS_Configuration {
    public String mavenServerId;
    public String profile = AWSProfileHandler.DEFAULT_PROFILE;
    public String region;
    public String domain="";
    public String domainOwner="";
    public boolean enabled;
}