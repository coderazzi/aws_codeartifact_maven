package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AWSInvoker {

    public interface Cancellable {
        boolean isCancelled();
    }

    public static OperationOutput getAuthToken(String domain,
                                               String domainOwner,
                                               String awsPath,
                                               Object awsProfile,
                                               String awsRegion,
                                               Cancellable cancellable) {
        // Do not send the profile if awsProfile is null or default
        String profile = awsProfile == null || "".equals(awsProfile) || awsProfile.equals(AWSProfileHandler.DEFAULT_PROFILE) ? "" :
                String.format("--profile %s ", awsProfile);
        String region = awsRegion == null || awsRegion.isBlank() ||
                awsRegion.equals(InputDialogState.DEFAULT_PROFILE_REGION) ? "" :
                String.format("--region %s ", awsRegion);
        String command = String.format(
                "%s codeartifact get-authorization-token %s%s--domain %s --domain-owner %s --query authorizationToken --output text",
                awsPath, profile, region, domain, domainOwner);
        OperationOutput ret = new OperationOutput();
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            ProcessReader inputReader = new ProcessReader(process.getInputStream());
            ProcessReader outputReader = new ProcessReader(process.getErrorStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancellable.isCancelled()) {
                    process.destroy();
                    return null;
                }
                String mfaRequest = outputReader.getMfaCodeRequest();
                if (mfaRequest != null) {
                    String mfaCode = MfaDialog.getMfaCode(mfaRequest);
                    if (mfaCode == null) {
                        process.destroy();
                        return null;
                    }
                    process.getOutputStream().write((mfaCode + "\n").getBytes(ENCODING));
                    process.getOutputStream().flush();
                }
            }
            if (0 == process.exitValue()) {
                ret.output = inputReader.getOutput();
                if (ret.output == null) {
                    ret.output = "No output collected from AWS command";
                } else {
                    ret.ok = true;
                }
            } else {
                ret.output = outputReader.getOutput();
                if (ret.output != null) {
                    ret.output = ret.output.trim();
                }
            }
        } catch (InvocationTargetException ex) {
            LOGGER.error(ex);
            ret.output = "Internal plugin error";

        } catch (Exception ex) {
            ret.output = "Error executing aws:" + ex.getMessage();
        }
        if (!profile.isEmpty() && ret.output.contains("aws configure")) {
            ret.output += "\n\n You could also consider \"aws configure " + profile.trim() + "\"";
        }
        return ret;
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
