package net.coderazzi.aws_codeartifact_maven.internal;

import com.intellij.openapi.progress.ProgressManager;
import net.coderazzi.aws_codeartifact_maven.settings.AppSettings;

public final class CredentialsUpdater {
    private CredentialsUpdater() {
        // utility class
    }

    public static OperationOutput runCredentialUpdateTask(AppSettings.State state) {
        return ProgressManager.getInstance().run(new CredentialsUpdateTask(state));
    }
}
