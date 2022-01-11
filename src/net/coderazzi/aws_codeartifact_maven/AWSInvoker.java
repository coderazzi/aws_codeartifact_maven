package net.coderazzi.aws_codeartifact_maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class AWSInvoker {

    public interface Cancellable {
        boolean isCancelled();
    }

    public static OperationOutput getCredentials(String domain, String domainOwner, String awsPath, String awsProfile,
                                                 Cancellable cancellable) {
        OperationOutput ret = new OperationOutput();
        try {
            String profile=awsProfile==null? "" : String.format(" --profile %s", awsProfile);
            Process process = Runtime.getRuntime().exec(String.format(
                    "%s codeartifact get-authorization-token %s --domain %s --domain-owner %s --query authorizationToken --output text",
                    awsPath, profile, domain, domainOwner));
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

        } catch (IOException | InterruptedException ex){
            ret.output = "Error executing aws:" + ex.getMessage();
        }
        return ret;
    }

    private static class ProcessReader implements Runnable {
        private InputStream inputStream;
        private Thread thread;
        private StringBuffer read = new StringBuffer();

        public ProcessReader(InputStream inputStream) {
            this.inputStream = inputStream;
            this.thread = new Thread(this);
            this.thread.start();
        }

        public String getOutput(){
            try {
                this.thread.join();
            } catch (InterruptedException ex){}
            return read.length()==0 ? null : read.toString();
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(this::readLine);
        }

        private void readLine(String line){
            if (!line.isEmpty()) {
                read.append(line);
            }
        }
    }

}
