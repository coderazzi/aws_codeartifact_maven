package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.ui.Messages;
import net.coderazzi.aws_codeartifact_maven.gui.GenerationDialog;

import javax.swing.*;

public abstract class InvokerController {

    private GenerationDialog generationDialog;

    public InvokerController(GenerationDialog dialog) {
        this.generationDialog = dialog;
    }

    public boolean isCancelled() {
        return generationDialog.isCancelled();
    }

    public abstract void showMessage(String message);

    public String requestMfaCode(String request) throws OperationException {
        final String []ret = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                Messages.InputDialog dialog = new Messages.InputDialog(
                        generationDialog.getProject(),
                        request,
                        "AWS input request",
                        null,
                        "",
                        new MfaCodeValidator());
                if (dialog.showAndGet()) {
                    ret[0] = dialog.getInputString();
                }
            });
        } catch(Exception iex) {
            throw new OperationException("Internal plugin error");
        }
        if (ret[0] == null || ret[0].isEmpty()) {
            throw new OperationException("No MFA code provided");
        }
        return ret[0];
    }
}
