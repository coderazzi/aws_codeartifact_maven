package net.coderazzi.aws_codeartifact_maven;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Set;

class ConfigurationDialog extends DialogWrapper {

    private final JTextField text = new JTextField(20);
    private final String name;
    private final Set<String> usedNames;

    public ConfigurationDialog(String name, Set<String> usedNames) {
        super(true); // use current window as parent
        this.name = name;
        this.usedNames = usedNames;
        init();
        if (name == null) {
            setTitle("Create new configuration");
            setOKButtonText("Create");
        } else {
            setTitle("Rename configuration");
            text.setText(name);
            setOKButtonText("Rename");
        }
        text.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                updateOkButtonState();
            }
        });
        setAutoAdjustable(true);
        updateOkButtonState();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return text;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(12, 0));
        centerPanel.add(new JLabel("Configuration name:"), BorderLayout.WEST);
        centerPanel.add(text, BorderLayout.EAST);
        return centerPanel;
    }

    public String getName() {
        return text.getText().trim();
    }

    private void updateOkButtonState() {
        String text = getName();
        setOKActionEnabled(!text.isEmpty() && (text.equals(name) || !usedNames.contains(text)));
    }

}