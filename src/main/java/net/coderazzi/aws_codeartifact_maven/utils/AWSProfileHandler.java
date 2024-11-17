package net.coderazzi.aws_codeartifact_maven.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// https://docs.aws.amazon.com/sdkref/latest/guide/file-location.html
public class AWSProfileHandler {

    public static final String DEFAULT_PROFILE = "default";
    // https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateInstanceProfile.html
    // Profile name pattern: [\w+=,.@-]+
    private static final Pattern configPattern =
            Pattern.compile("^\\s*\\[\\s*profile\\s+([\\w+=,.@-]+)\\s*]\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern credentialsPattern =
            Pattern.compile("^\\s*\\[\\s*([\\w+=,.@-]+)\\s*]\\s*$",
            Pattern.CASE_INSENSITIVE);

    public static Set<String> getDefaultProfiles() {
        Set<String> ret = new TreeSet<>();
        ret.add(DEFAULT_PROFILE);
        return ret;

    }

    public static Set<String> getProfiles() throws GetProfilesException {
        Set<String> ret = getDefaultProfiles();
        Map<Path, Pattern> files = new HashMap<>();
        String home = System.getProperty("user.home");
        String configEnv = System.getenv("AWS_CONFIG_FILE");
        String credentialsEnv = System.getenv("AWS_SHARED_CREDENTIALS_FILE");
        if (configEnv != null) {
            files.put(Paths.get(configEnv), configPattern);
        }
        if (credentialsEnv != null) {
            files.put(Paths.get(credentialsEnv), credentialsPattern);
        }
        if (home != null) {
            Path awsPath = Paths.get(home).resolve(".aws");
            files.put(awsPath.resolve("config"), configPattern);
            files.put(awsPath.resolve("credentials"), credentialsPattern);
        }
        if (files.isEmpty()) {
            throw new GetProfilesException("Cannot read AWS config or credentials files");
        }
        AtomicBoolean somethingRead = new AtomicBoolean(false);
        files.forEach((path, pattern) -> {
            if (Files.isReadable(path) && getProfiles(ret, path, pattern)) {
                somethingRead.set(true);
            }
        });
        if (somethingRead.get()) {
            return ret;
        }
        throw new GetProfilesException("Error accessing AWS files: " +
                files.keySet().stream().map(k -> k.normalize().toString()).collect(Collectors.joining(", ")));
    }

    private static boolean getProfiles(Set<String> profiles, Path path, Pattern pattern) {
        try (Stream<String> lines = Files.lines(path, Charset.defaultCharset())) {
            lines.forEach(line -> {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    profiles.add(m.group(1));
                }
            });
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    public static class GetProfilesException extends Exception {
        GetProfilesException(String ex) {
            super(ex);
        }
    }

}
