package net.coderazzi.aws_codeartifact_maven.internal.aws;

import com.intellij.openapi.ui.DialogWrapper;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.jetbrains.annotations.Nullable;

class AwsMfaDialog extends DialogWrapper {

    public static final String TITLE = "AWS MFA Request";
    private final JTextField mfa = new JTextField(6);
    private final String request;

    public AwsMfaDialog(String awsRequest) {
        super(true); // use current window as parent
        request = awsRequest;
        init();
        setTitle(TITLE);
        setAutoAdjustable(true);
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return mfa;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        var centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JLabel(request), BorderLayout.WEST);
        centerPanel.add(mfa, BorderLayout.EAST);
        return centerPanel;
    }

    public String getMfaCode() {
        return mfa.getText();
    }

    public static String getMfaCode(final String request) throws InvocationTargetException {
        var code = new AtomicReference<>("");
        try {
            SwingUtilities.invokeAndWait(() -> {
                var dialog = new AwsMfaDialog(request);
                if (dialog.showAndGet()) {
                    code.set(dialog.getMfaCode());
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return code.get();
    }
}
