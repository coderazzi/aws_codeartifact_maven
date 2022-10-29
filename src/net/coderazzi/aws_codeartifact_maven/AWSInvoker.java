package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class AWSInvoker {

    public interface Cancellable {
        boolean isCancelled();
    }

    public static OperationOutput getCredentials(String domain, String domainOwner, String awsPath, String awsProfile,
                                                 Cancellable cancellable) {
        // Do not send the profile if awsProfile is null or default
        String profile = awsProfile == null || awsProfile.equals(AWSProfileHandler.DEFAULT_PROFILE) ? "" :
                String.format("--profile %s ", awsProfile);
        String command = String.format(
                "%s codeartifact get-authorization-token %s--domain %s --domain-owner %s --query authorizationToken --output text",
                awsPath, profile, domain, domainOwner);
        OperationOutput ret = new OperationOutput();
        try {
            LOGGER.debug(command);
            Process process = Runtime.getRuntime().exec(command);
            ProcessReader inputReader = new ProcessReader(process.getInputStream());
            ProcessReader errorReader = new ProcessReader(process.getErrorStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancellable.isCancelled()) {
                    process.destroy();
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
                ret.output = errorReader.getOutput();
            }

        } catch (Exception ex) {
            ret.output = "Error executing aws:" + ex.getMessage();
        }
        if (ret.output != null && !profile.isEmpty() && ret.output.contains("aws configure")) {
            ret.output+="\n\n You could also consider \"aws configure " + profile + "\"";
        }
        return ret;
    }

    private static class ProcessReader implements Runnable {
        private final InputStream inputStream;
        private final Thread thread;
        private final StringBuffer read = new StringBuffer();

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
            return read.length() == 0 ? null : read.toString();
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(this::readLine);
        }

        private void readLine(String line) {
            if (!line.isEmpty()) {
                read.append(line);
            }
        }
    }

    private static Logger LOGGER = Logger.getInstance(AWSInvoker.class);

}
