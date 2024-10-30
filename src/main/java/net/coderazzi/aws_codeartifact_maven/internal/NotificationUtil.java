package net.coderazzi.aws_codeartifact_maven.internal;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;

public final class NotificationUtil {

    private NotificationUtil() {
        // utility class
    }

    public static void renderOutput(OperationOutput output) {
        var notification = builderNotification(output);

        notification.addAction(new AnAction("Configure Plugin") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(null, "AWS CodeArtifact Maven");
            }
        });

        Notifications.Bus.notify(notification);
    }

    private static @NotNull Notification builderNotification(OperationOutput output) {
        if (output.success()) {
            return new Notification("net.coderazzi", "AWS CodeArtifact Maven Plugin",
                    "Successfully updated credentials",
                    NotificationType.INFORMATION);
        }

        return new Notification("net.coderazzi", "AWS CodeArtifact Maven Plugin",
                "Failed to update credentials: " + (output.output() != null ? output.output() : "<no output>"),
                NotificationType.ERROR);
    }
}
