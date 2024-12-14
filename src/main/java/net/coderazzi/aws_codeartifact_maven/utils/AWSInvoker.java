package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.diagnostic.Logger;
import net.coderazzi.aws_codeartifact_maven.state.Configuration;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AWSInvoker  {

    private final static Logger LOGGER = Logger.getInstance(AWSInvoker.class);
    private final static String ENCODING = "UTF-8"; // python 3 (aws cli) encoding
    private final Pattern MFA_PATTERN = Pattern.compile(".*?(Enter MFA code for \\S+\\s)$", Pattern.DOTALL);
    private final Pattern ssoPattern = Pattern.compile(".*SSO authorization.*enter the code:\\s+(\\S+)\\s.*", Pattern.DOTALL);

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

    /**
     * Returns the authentication token, or raises an OperationException
     */
    public String getAuthToken() throws OperationException {
        String ret = invoke(command, this::handleMfaRequest, this::handleSsoError);
        if (ret == null) {
            throw new OperationException("No output collected from AWS command");
        }
        return ret;
    }

    private void handleSsoRequest(Process process,
                                  ProcessReader inputReader,
                                  ProcessReader outputReader)
            throws OperationException {
        // This method is called when we start the aws sso login invocation
        // During the invocation, the AWS cli will present a code to be
        // verified in the browser, so we just display it, without further action
        Matcher m = ssoPattern.matcher(inputReader.read());
        if (m.matches()) {
            inputReader.reset();
            controller.showMessage("SSO login: code " + m.group(1));
        }
    }

    private void handleMfaRequest(Process process,
                                  ProcessReader inputReader,
                                  ProcessReader outputReader)
            throws OperationException, IOException {
        Matcher m = MFA_PATTERN.matcher(outputReader.read());
        if (m.matches()) {
            outputReader.reset();
            String mfaCode = controller.requestMfaCode( m.group(1));
            if (mfaCode == null || mfaCode.isEmpty()) {
                throw new OperationException("No MFA code provided");
            }
            // we enter now the provided mfa code in the process output
            process.getOutputStream().write((mfaCode + "\n").getBytes(ENCODING));
            process.getOutputStream().flush();
        }
    }


    private String handleSsoError(String error) throws OperationException {
        if (error.startsWith("Error loading SSO Token")
                || error.startsWith("Error when retrieving token from sso"))  {
            try {
                invoke("aws sso login" + profile, this::handleSsoRequest, ErrorHandler.NULL);
            } catch (OperationException oex) {
                String message = oex.getMessage();
                throw message == null? oex : new OperationException("SSO login: " + message);
            }
            // we invoke again the original command.
            // we do not expect now any MFA or SSO handling, as SSO is already completed
            return invoke(command, RequestHandler.NULL, ErrorHandler.NULL);
        }
        return null;
    }

    private String invoke(@NotNull  String command,
                          @NotNull RequestHandler requestHandler,
                          @NotNull ErrorHandler errorHandler) throws OperationException {
        controller.checkCancellation();
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            try {
                ProcessReader inputReader = new ProcessReader(process.getInputStream());
                ProcessReader outputReader = new ProcessReader(process.getErrorStream());
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    controller.checkCancellation();
                    requestHandler.handle(process, inputReader, outputReader);
                }
                if (process.exitValue() == 0) {
                    return inputReader.getOutput();
                }
                String error = outputReader.getOutput();
                if (error == null) {
                    error = "Auth token request failed without additional information";
                } else {
                    String ret = errorHandler.handle(error = error.trim());
                    if (ret != null) {
                        return ret;
                    }
                    if (!profile.isEmpty() && error.contains("aws configure")) {
                        error += "\n\n You could also consider \"aws configure " + profile.trim() + "\"";
                    }
                }
                throw new OperationException(error);
            } finally {
                process.destroy();
            }
        } catch (OperationException  oex) {
            throw oex;
        } catch (Exception ex) {
            throw new OperationException("Error executing aws:" + ex.getMessage());
        }
    }

    /**
     * Utility class to handle an input stream asynchronously
     */
    static class ProcessReader implements Runnable {
        private final InputStream inputStream;
        private final Thread thread;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

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
            String read = read();
            return read.isEmpty() ? null : read;
        }

        public void reset(){
            byteArrayOutputStream.reset();
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

        public String read() {
            try {
                return byteArrayOutputStream.toString(ENCODING);
            } catch (UnsupportedEncodingException ex) {
                LOGGER.error(ex);
                return "";
            }
        }
    }

    /**
     * Interface used while the AWS cli is invoked, to handle any
     * input / output provided by AWS
     */
    interface RequestHandler {

        void handle(Process process,
                    ProcessReader inputReader,
                    ProcessReader outputReader)
                throws OperationException, IOException;

        RequestHandler NULL =
                (a, b, c) -> {};
    }

    /**
     * Interface used to handle errors provided by the AWS cli
     */
    interface ErrorHandler {
        /**
         * Returns a non-null string if the error is recovered
         */
        String handle(String error) throws OperationException;

        ErrorHandler NULL = (e ) -> null;
    }


}
