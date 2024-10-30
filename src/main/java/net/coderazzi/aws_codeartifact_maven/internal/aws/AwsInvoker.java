package net.coderazzi.aws_codeartifact_maven.internal.aws;

import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.coderazzi.aws_codeartifact_maven.internal.OperationOutput;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;
import org.jetbrains.annotations.NotNull;

public class AwsInvoker {
    private static final Logger LOGGER = Logger.getInstance(AwsInvoker.class);

    private AwsInvoker() {
        // utility class
    }

    public static OperationOutput getCredentials(AppSettings.State state) {
        try {
            var command = getCommand(state);
            var process = Runtime.getRuntime().exec(command);
            var inputReader = new ProcessReader(process.getInputStream());
            var outputReader = new ProcessReader(process.getErrorStream());
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                var mfaRequest = outputReader.getMfaCodeRequest();
                if (mfaRequest != null) {
                    var mfaCode = AwsMfaDialog.getMfaCode(mfaRequest);
                    if (mfaCode == null) {
                        process.destroy();
                        return null;
                    }
                    process.getOutputStream().write((mfaCode + "\n").getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().flush();
                }
            }
            return getOperationOutput(inputReader, 0 == process.exitValue());
        } catch (InvocationTargetException ex) {
            LOGGER.error("Error showing MFA dialog", ex);
            return new OperationOutput(false, "Internal plugin error");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new OperationOutput(false, "Interrupted");
        } catch (Exception ex) {
            return new OperationOutput(false, ex.getMessage());
        }
    }

    private static OperationOutput getOperationOutput(ProcessReader inputReader, boolean success) {
        var output = inputReader.getOutput();
        if (output == null) {
            return new OperationOutput(false, "No output collected from AWS command");
        } else {
            return new OperationOutput(success, output.trim());
        }
    }

    private static @NotNull String getCommand(AppSettings.State state) {
        if (state.getDomain() == null || state.getDomain().isBlank()) {
            throw new IllegalArgumentException("CodeArtifact Domain is required");
        }
        if (state.getAwsAccountId() == null || state.getAwsAccountId().isBlank()) {
            throw new IllegalArgumentException("AWS Account ID is required");
        }

        var sb = new StringBuilder();
        sb.append(state.getAwsPath());
        sb.append(" codeartifact get-authorization-token ");

        if (state.getAwsProfile() != null && !state.getAwsProfile().isBlank()) {
            // Do not send the profile if awsProfile is null or default
            sb.append(String.format("--profile %s ", state.getAwsProfile()));
        }
        if (state.getRegion() != null && !state.getRegion().isBlank()) {
            sb.append(String.format("--region %s ", state.getRegion()));
        }
        if (state.getRefreshIntervalMinutes() > 0) {
            sb.append(String.format("--duration-seconds %d ", state.getRefreshIntervalMinutes() * 60));
        }

        sb.append(String.format("--domain %s ", state.getDomain()));
        sb.append(String.format("--domain-owner %s ", state.getAwsAccountId()));
        sb.append("--query authorizationToken ");
        sb.append("--output text");

        return sb.toString();
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
                Thread.currentThread().interrupt();
            }
            var read = getRead();
            return read.isEmpty() ? null : read;
        }

        public synchronized String getMfaCodeRequest() {
            var m = mfaPattern.matcher(getRead());
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
                    var b = inputStream.read();
                    if (b == -1) {
                        break;
                    }
                    byteArrayOutputStream.write(b);
                }
            } catch (IOException ex) {
                var b = "Error reading AWS output".getBytes(StandardCharsets.UTF_8);
                byteArrayOutputStream.reset();
                byteArrayOutputStream.write(b, 0, b.length);
            }
        }

        private String getRead() {
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
