package net.coderazzi.aws_codeartifact_maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class AWSTask {

    public interface Cancellable {
        boolean isCancelled();
    }

    public static TaskOutput getCredentials(String domain, String domainOwner, String awsPath,
                                            Cancellable cancellable) {
        TaskOutput ret = new TaskOutput();
        try {
            Process process = Runtime.getRuntime().exec(String.format(
                    "%s codeartifact get-authorization-token --domain %s --domain-owner %s --query authorizationToken --output text",
                    awsPath, domain, domainOwner));
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
        private String read;

        public ProcessReader(InputStream inputStream) {
            this.inputStream = inputStream;
            new Thread(this).start();
        }

        public String getOutput(){
            return read;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(this::readLine);
        }

        private void readLine(String line){
            if (read==null && !line.isEmpty()) {
                read = line;
            }
        }
    }

}
