package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;

public class MfaDialog extends DialogWrapper {

    public static final String TITLE = "AWS input request";
    private final JTextField mfa = new JTextField(6);
    private final String request;

    public MfaDialog(Project project, String awsRequest) {
        super(project, true); // use current window as parent
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

}