package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.ui.Messages;
import net.coderazzi.aws_codeartifact_maven.gui.GenerationDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class InvokerController {

    private final GenerationDialog generationDialog;

    public InvokerController(GenerationDialog dialog) {
        this.generationDialog = dialog;
    }

    public void checkCancellation() throws OperationException {
        if (generationDialog.isCancelled()) {
            throw OperationException.cancelled();
        }
    }

    /**
     * Shows a short message to be displayed on the generation dialog.
     * @param shortMessage can be null
     * @param popupMessage a message to be displayed immediately on a popup, can be null
     */
    public abstract void showMessage(@NotNull String shortMessage, @Nullable String popupMessage);

    public String requestMfaCode(String request) throws OperationException {
        final String []hold = new String[1];
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
                    hold[0] = dialog.getInputString();
                }
            });
        } catch(Exception iex) {
            throw new OperationException("Internal plugin error");
        }
        return hold[0];
    }
}
