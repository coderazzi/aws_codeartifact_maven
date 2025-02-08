package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.diagnostic.Logger;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AWSInvoker  {

    private final static Logger LOGGER = Logger.getInstance(AWSInvoker.class);
    private final static String ENCODING = "UTF-8"; // python 3 (aws cli) encoding

    private final InvokerController controller;
    private final String command, profile;

    public AWSInvoker(InvokerController controller,
                      String domain,
                      String domainOwner,
                      String awsPath,
                      Object awsProfile,
                      String awsRegion) {
        this.controller = controller;
        String region = awsRegion == null || awsRegion.isBlank() ||
                awsRegion.equals(Configuration.DEFAULT_PROFILE_REGION) ? "" :
                String.format("--region %s ", awsRegion);
        // Do not send the profile if awsProfile is null or default
        this.profile = awsProfile == null || "".equals(awsProfile) || awsProfile.equals(AWSProfileHandler.DEFAULT_PROFILE) ? "" :
                String.format(" --profile %s ", awsProfile);
        this.command = String.format(
                "%s codeartifact get-authorization-token %s--domain %s --domain-owner %s --query authorizationToken --output text%s",
                awsPath, region, domain, domainOwner, profile);
    }

    public String getAuthToken() throws OperationException {
        String ret = invoke(command, this::handleMfaRequest, this::handleSsoError);
        if (ret == null) {
            throw new OperationException("No output collected from AWS command");
        }
        return ret;
    }

    private boolean handleSsoRequest(Process process,
                                     ProcessReader inputReader,
                                     ProcessReader outputReader)
            throws OperationException {
        String ssoCode = inputReader.getSsoCheckingCode();
        if (ssoCode != null) {
            controller.showMessage("SSO login: code " + ssoCode);
        }
        return false;
    }

    private boolean handleMfaRequest(Process process,
                                     ProcessReader inputReader,
                                     ProcessReader outputReader)
            throws OperationException, IOException {
        String mfaRequest = outputReader.getMfaCodeRequest();
        if (mfaRequest != null) {
            String mfaCode = controller.requestMfaCode(mfaRequest);
            if (mfaCode == null) {
                process.destroy();
                return true;
            }
            process.getOutputStream().write((mfaCode + "\n").getBytes(ENCODING));
            process.getOutputStream().flush();
        }
        return false;
    }


    private String handleSsoError(String error) throws OperationException{
        if ((error.startsWith("Error loading SSO Token")
                || error.startsWith("Error when retrieving token from sso"))
                && !controller.isCancelled()) {
            try {
                invoke("aws sso login" + profile, this::handleSsoRequest, null);
            } catch (OperationException oex) {
                throw new OperationException("SSO login: " + oex.getMessage());
            }
            if (!controller.isCancelled()) {
                return invoke(command, null, null);
            }
        }
        return null;
    }

    private String invoke(String command, RequestHandler requestHandler, ErrorHandler errorHandler) throws OperationException {
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
                if (requestHandler !=null && requestHandler.handle(process, inputReader, outputReader)) {
                    return null;
                }
            }
            if (process.exitValue() == 0) {
                return inputReader.getOutput();
            }
            String error = outputReader.getOutput();
            if (error == null) {
                error = "Auth token request failed without additional information";
            } else {
                error = error.trim();
                String ret = errorHandler==null? null : errorHandler.handle(error);
                if (ret != null) {
                    return ret;
                }
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

    static class ProcessReader implements Runnable {
        private final InputStream inputStream;
        private final Thread thread;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        private final Pattern mfaPattern = Pattern.compile(".*?(Enter MFA code for \\S+\\s)$", Pattern.DOTALL);
        private final Pattern ssoPattern = Pattern.compile(".*SSO authorization.*enter the code:\\s+(\\S+)\\s.*", Pattern.DOTALL);

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

        public synchronized String getSsoCheckingCode() {
            Matcher m = ssoPattern.matcher(getRead());
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

    interface RequestHandler {
        boolean handle(Process process,
                       ProcessReader inputReader,
                       ProcessReader outputReader)
                throws OperationException, IOException;
    }

    interface ErrorHandler {
        String handle(String error) throws OperationException;
    }


}
