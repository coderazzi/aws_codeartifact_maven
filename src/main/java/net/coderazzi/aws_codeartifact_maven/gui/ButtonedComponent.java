package net.coderazzi.aws_codeartifact_maven.gui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ButtonedComponent <J extends JComponent> extends JPanel{
    private final J mainComponent;
    private final FixedSizeButton button;

    public ButtonedComponent(J component) {
        super(new BorderLayout(!SystemInfo.isMac && !JBColor.isBright() ? 2 : 0, 0));
        this.mainComponent = component;
        this.setFocusable(false);
        this.button = new FixedSizeButton(this.mainComponent);
        this.add(this.mainComponent, "Center");
        this.add(this.button, "East");
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.button.setEnabled(enabled);
        this.mainComponent.setEnabled(enabled);
    }

    public void setButtonIcon(Icon icon) {
        this.button.setIcon(icon);
        this.button.setDisabledIcon(IconLoader.getDisabledIcon(icon));
    }

    public void setBackground(Color color) {
        super.setBackground(color);
        if (this.button != null) {
            this.button.setBackground(color);
        }
    }

    public void addBrowseAction(String title, FileChooserDescriptor descriptor) {
        final JTextField textField = (JTextField) mainComponent;
        descriptor.setTitle(title);
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(textField.getText());
                VirtualFile chosenFile = FileChooser.chooseFile(descriptor, null, vf);
                if (chosenFile != null) {
                    textField.setText(chosenFile.getPath());
                    for (ActionListener each : textField.getActionListeners()) {
                        each.actionPerformed(e);
                    }
                }

            }
        });
    }

    public void addActionListener(ActionListener listener) {
        this.button.addActionListener(listener);
    }
}

