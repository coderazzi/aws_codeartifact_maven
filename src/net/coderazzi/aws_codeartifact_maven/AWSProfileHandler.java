package net.coderazzi.aws_codeartifact_maven;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class AWSProfileHandler {

    // https://docs.aws.amazon.com/IAM/latest/APIReference/API_CreateInstanceProfile.html
    // Profile name pattern: [\w+=,.@-]+
    private static Pattern pattern = Pattern.compile("^\\s*\\[\\s*profile\\s+([\\w+=,.@-]+)\\s*]\\s*$",
            Pattern.CASE_INSENSITIVE);
    public static final String DEFAULT_PROFILE = "default";

    public static Set<String> getProfiles() throws GetProfilesException {
        String home = System.getProperty("user.home");
        if (home == null) {
            throw new GetProfilesException("Cannot find aws config file, user.home not defined");
        }
        Path configPath = Paths.get(home).resolve(".aws").resolve("config");
        final Set<String> ret = new TreeSet<>();
        ret.add(DEFAULT_PROFILE);
        try (Stream<String> lines = Files.lines(configPath, Charset.defaultCharset())) {
            lines.forEach(line -> {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    ret.add(m.group(1));
                }
            });
        } catch (IOException ex) {
            throw new GetProfilesException(String.format("Error accessing config file %s: %s",
                    configPath, ex.getMessage()));
        }
        return ret;
    }

    static class GetProfilesException extends Exception {
        GetProfilesException(String ex) { super(ex); }
    }

}
