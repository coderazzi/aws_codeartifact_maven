package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.lang.reflect.InvocationTargetException;

class MfaDialog extends DialogWrapper {

    public static final String TITLE = "AWS input request";
    private final JTextField mfa = new JTextField(6);
    private final String request;

    public MfaDialog(String awsRequest) {
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
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JLabel(request), BorderLayout.WEST);
        centerPanel.add(mfa, BorderLayout.EAST);
        return centerPanel;
    }


    public String getMfaCode() {
        return mfa.getText();
    }

    public static String getMfaCode(final String request) throws InvocationTargetException {
        final DialogStatus status = new DialogStatus();
        try {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                final MfaDialog dialog = new MfaDialog(request);
                if (dialog.showAndGet()) {
                    status.code = dialog.getMfaCode();
                }
            });
        } catch (InterruptedException ex) {
            // being terminated, logging anything would help no one...
        }
        return status.code;
    }

    private static class DialogStatus {
        public String code;
    }

}