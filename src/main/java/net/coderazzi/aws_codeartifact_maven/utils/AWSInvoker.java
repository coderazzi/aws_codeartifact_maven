package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.diagnostic.Logger;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AWSInvoker {

    public interface BackgroundController {
        boolean isCancelled();
        String requestMfaCode(String request) throws OperationException;
    }

    private final String awsPath, awsVaultPath;
    private final BackgroundController controller;
    private List<String> vaultProfiles;

    public AWSInvoker(BackgroundController controller, String awsPath, String awsVaultPath) {
        this.controller = controller;
        this.awsPath = awsPath;
        this.awsVaultPath = awsVaultPath;
    }

    public String getAuthToken(String domain,
                               String domainOwner,
                               Object awsProfile,
                               String awsRegion) throws OperationException {
        // Do not send the profile if awsProfile is null or default
        String region = awsRegion == null || awsRegion.isBlank() ||
                awsRegion.equals(Configuration.DEFAULT_PROFILE_REGION) ? "" :
                String.format("--region %s ", awsRegion);
        String profileName = awsProfile == null || "".equals(awsProfile) ? AWSProfileHandler.DEFAULT_PROFILE : awsProfile.toString();
        boolean isVaultProfile = isVaultProfile(profileName);
        String profile = isVaultProfile || AWSProfileHandler.DEFAULT_PROFILE.equals(profileName) ? "" :
                String.format("--profile %s ", profileName);
        String command = String.format(
                "%s codeartifact get-authorization-token %s%s--domain %s --domain-owner %s --query authorizationToken --output text",
                awsPath, profile, region, domain, domainOwner);
        if (isVaultProfile) {
            command = String.format("%s exec %s -- %s", awsVaultPath, profileName, command);
        }
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            StreamReader outputReader = new StreamReader("aws output", process.getInputStream());
            MfaHandler errorReader = new MfaHandler(process.getErrorStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (controller.isCancelled()) {
                    process.destroy();
                    return null;
                }
                String mfaRequest = errorReader.getMfaCodeRequest();
                if (mfaRequest != null) {
                    String mfaCode = controller.requestMfaCode(mfaRequest);
                    if (mfaCode == null) {
                        process.destroy();
                        return null;
                    }
                    process.getOutputStream().write((mfaCode + "\n").getBytes(StreamReader.ENCODING));
                    process.getOutputStream().flush();
                }
            }
            if (process.exitValue() == 0) {
                String ret = outputReader.getOutput();
                if (ret == null) {
                    throw new OperationException("No output collected from AWS command");
                }
                return ret;
            }
            String error = errorReader.getOutput();
            if (error == null) {
                error = "Auth token request failed without additional information";
            } else {
                error = error.trim();
                if (!profile.isEmpty() && error.contains("aws configure")) {
                    error += "\n\n You could also consider \"aws configure " + profile.trim() + "\"";
                }
            }
            throw new OperationException(error);
        } catch (OperationException oex) {
            throw oex;
        } catch (Exception ex) {
            throw new OperationException("Error executing aws:" + ex.getMessage());
        }
    }

    private synchronized boolean isVaultProfile(String awsProfile) throws OperationException {
        if (awsVaultPath == null) {
            return false;
        }
        if (vaultProfiles == null) {
            vaultProfiles = new ArrayList<>();
            for (String line : getAwsVaultListOutput().split("\n") ) {
                line = line.trim();
                if (!line.isEmpty()) {
                    vaultProfiles.add(line);
                }
            }
        }
        return vaultProfiles.contains(awsProfile);
    }

    private String getAwsVaultListOutput() throws OperationException{
        String command = String.format("%s list --credentials", awsVaultPath);
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            StreamReader outputReader = new StreamReader("aws-vault output", process.getInputStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (controller.isCancelled()) {
                    process.destroy();
                    return "";
                }
            }
            if (process.exitValue() == 0) {
                String ret = outputReader.getOutput();
                if (ret == null) {
                    throw new OperationException("No output collected from aws-vault command");
                }
                return ret;
            }
            String error = outputReader.getOutput();
            if (error == null) {
                error = "Error invoking " + awsVaultPath;
            } else {
                error = error.trim();
            }
            throw new OperationException(error);
        } catch (OperationException oex) {
            throw oex;
        } catch (Exception ex) {
            throw new OperationException("Error executing aws-vault:" + ex.getMessage());
        }
    }

    private static class MfaHandler extends StreamReader {
        private final Pattern mfaPattern = Pattern.compile(".*?(Enter MFA code for \\S+\\s)$", Pattern.DOTALL);

        public MfaHandler(InputStream inputStream) {
            super("aws input", inputStream);
        }

        public synchronized String getMfaCodeRequest() {
            Matcher m = mfaPattern.matcher(getRead());
            if (m.matches()) {
                byteArrayOutputStream.reset();
                return m.group(1);
            }
            return null;
        }

    }

    private final static Logger LOGGER = Logger.getInstance(AWSInvoker.class);

}
