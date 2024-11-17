package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.diagnostic.Logger;
import net.coderazzi.aws_codeartifact_maven.gui.MfaDialog;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AWSInvoker {

    public interface BackgroundController {
        boolean isCancelled();
        String requestMfaCode(String request) throws OperationException;
    }

    public static String getAuthToken(String domain,
                                      String domainOwner,
                                      String awsPath,
                                      Object awsProfile,
                                      String awsRegion,
                                      BackgroundController controller) throws OperationException {
        // Do not send the profile if awsProfile is null or default
        String profile = awsProfile == null || "".equals(awsProfile) || awsProfile.equals(AWSProfileHandler.DEFAULT_PROFILE) ? "" :
                String.format("--profile %s ", awsProfile);
        String region = awsRegion == null || awsRegion.isBlank() ||
                awsRegion.equals(Configuration.DEFAULT_PROFILE_REGION) ? "" :
                String.format("--region %s ", awsRegion);
        String command = String.format(
                "%s codeartifact get-authorization-token %s%s--domain %s --domain-owner %s --query authorizationToken --output text",
                awsPath, profile, region, domain, domainOwner);
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            ProcessReader inputReader = new ProcessReader(process.getInputStream());
            ProcessReader outputReader = new ProcessReader(process.getErrorStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (controller.isCancelled()) {
                    process.destroy();
                    return null;
                }
                String mfaRequest = outputReader.getMfaCodeRequest();
                if (mfaRequest != null) {
                    String mfaCode = controller.requestMfaCode(mfaRequest);
                    if (mfaCode == null) {
                        process.destroy();
                        return null;
                    }
                    process.getOutputStream().write((mfaCode + "\n").getBytes(ENCODING));
                    process.getOutputStream().flush();
                }
            }
            if (process.exitValue() == 0) {
                String ret = inputReader.getOutput();
                if (ret == null) {
                    throw new OperationException("No output collected from AWS command");
                }
                return ret;
            }
            String error = outputReader.getOutput();
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

    private static class ProcessReader implements Runnable {
        private final InputStream inputStream;
        private final Thread thread;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        private final Pattern mfaPattern = Pattern.compile(".*?(Enter MFA code for \\S+\\s)$", Pattern.DOTALL);

        public ProcessReader(InputStream inputStream) {
            this.inputStream = inputStream;
            this.thread = new Thread(this);
            this.thread.start();
        }

        public String getOutput() {
            try {
                this.thread.join();
            } catch (InterruptedException ex) {
                // thread interrupted, app being stopped, nothing else to do here
            }
            String read = getRead();
            return read.isEmpty() ? null : read;
        }

        public synchronized String getMfaCodeRequest() {
            Matcher m = mfaPattern.matcher(getRead());
            if (m.matches()) {
                byteArrayOutputStream.reset();
                return m.group(1);
            }
            return null;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int b = inputStream.read();
                    if (b == -1) {
                        break;
                    }
                    byteArrayOutputStream.write(b);
                }
            } catch (IOException ex) {
                try {
                    byte[] b = "Error reading AWS output".getBytes(ENCODING);
                    byteArrayOutputStream.reset();
                    byteArrayOutputStream.write(b, 0, b.length);
                } catch (UnsupportedEncodingException uex) {
                    LOGGER.error(uex);
                }
            }
        }

        private String getRead() {
            try {
                return byteArrayOutputStream.toString(ENCODING);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.error(ex);
                return "";
            }
        }
    }

    private final static Logger LOGGER = Logger.getInstance(AWSInvoker.class);
    private final static String ENCODING = "UTF-8"; // python 3 (aws cli) encoding

}
