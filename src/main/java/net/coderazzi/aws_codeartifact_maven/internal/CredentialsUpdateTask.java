package net.coderazzi.aws_codeartifact_maven.internal;

import static java.util.Objects.requireNonNull;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import net.coderazzi.aws_codeartifact_maven.internal.aws.AwsInvoker;
import net.coderazzi.aws_codeartifact_maven.internal.maven.MavenSettingsManager;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;
import org.jetbrains.annotations.NotNull;

class CredentialsUpdateTask extends Task.WithResult<OperationOutput, IllegalStateException> {

    private final AppSettings.State state;

    public CredentialsUpdateTask(AppSettings.State state) {
        super(null, null, "Updating AWS CodeArtifact Maven Credentials", false);
        this.state = state;
    }

    @Override
    protected OperationOutput compute(@NotNull ProgressIndicator progressIndicator) {
        progressIndicator.setFraction(0.10);
        progressIndicator.setText("Locating Maven server");

        progressIndicator.setFraction(0.50);
        progressIndicator.setText("Retrieving AWS credentials");

        var awsGetTokenTask = requireNonNull(AwsInvoker.getCredentials(state));
        if (!awsGetTokenTask.success()) {
            return awsGetTokenTask;
        }

        progressIndicator.setFraction(0.90);
        progressIndicator.setText("Updating Maven settings");

        var password = awsGetTokenTask.output();
        var mavenSettingsTask = MavenSettingsManager.updateServerCredentials(state.getMavenSettingsFile(), state.getMavenServerId(), "aws", password);
        if (!mavenSettingsTask.success()) {
            return mavenSettingsTask;
        }

        progressIndicator.setText("Maven Settings updated");
        progressIndicator.setFraction(1.0);

        return new OperationOutput(true, "Successfully updated credentials");
    }
}
