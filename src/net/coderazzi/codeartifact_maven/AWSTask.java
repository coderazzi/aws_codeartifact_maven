package net.coderazzi.codeartifact_maven;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class AWSTask {

    public static TaskOutput getCredentials(String domain, String domainOwner) {
        TaskOutput ret = new TaskOutput();
        try {
            Process process = Runtime.getRuntime().exec(String.format(
                    "aws codeartifact get-authorization-token --domain %s --domain-owner %s --query authorizationToken --output text",
                    domain, domainOwner));
            ProcessReader inputReader = new ProcessReader(process.getInputStream());
            ProcessReader errorReader = new ProcessReader(process.getErrorStream());
            if (0 == process.waitFor()) {
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
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach((line)->readLine(line));
        }

        private void readLine(String line){
            if (read==null && !line.isEmpty()) {
                read = line;
            }
        }
    }

}
