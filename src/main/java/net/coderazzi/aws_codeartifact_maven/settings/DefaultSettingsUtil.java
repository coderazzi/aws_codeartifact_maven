package net.coderazzi.aws_codeartifact_maven.settings;

import java.nio.file.Paths;
import net.coderazzi.aws_codeartifact_maven.internal.maven.MavenSettingsManager;

public final class DefaultSettingsUtil {
    private DefaultSettingsUtil() {
        // utility class
    }

    public static String getDefaultMavenServerId(String mavenSettingsFile) {
        return MavenSettingsManager.findServerIdByUsername(mavenSettingsFile, "aws")
                .orElse("<not a single server id found>");
    }

    public static String getDefaultMavenSettingsPath() {
        String home = System.getProperty("user.home");
        if (home != null) {
            return Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
        }
        return "";
    }

    public static String getDefaultAwsProfile() {
        String profile = System.getenv("AWS_PROFILE");
        if (profile != null) {
            return profile.trim();
        }
        return "default";
    }

    public static String getDefaultAwsPath() {
        return "aws";
    }
}
