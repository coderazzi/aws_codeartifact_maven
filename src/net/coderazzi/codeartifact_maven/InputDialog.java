package net.coderazzi.codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;

class InputDialog extends DialogWrapper {

    private final static String PROPERTIES_PREFIX="net.coderazzi.codeartifact_maven";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final JTextField mavenServerId = new JTextField(32);
    private final JTextField mavenSettingsFile = new JTextField(32);

    private final PropertiesComponent properties;

    public InputDialog() {
        super(true); // use current window as parent
        properties = PropertiesComponent.getInstance();
        init();
        setTitle("Generate AWS CodeArtifact credentials");
        setAutoAdjustable(true);
        setOKButtonText("Generate credentials");
    }

    public String getDomain(){
        return getPropertiesValue("domain");
    }

    public String getDomainOwner(){
        return getPropertiesValue("domainOwner");
    }

    public String getMavenServerId(){
        return getPropertiesValue("mavenServerId");
    }

    public String getMavenServerSettingsFile(){
        String ret = getPropertiesValue("mavenSettingsFile");
        if (ret.isEmpty()) {
            String home = System.getProperty("user.home");
            if (home != null){
                ret = Paths.get(home).resolve(".m2").resolve("settings.xml").toString();
            }
        }
        return ret;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        GridBag gridbag = new GridBag()
                .setDefaultWeightX(1.0)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)
                .setDefaultInsets(new Insets(0, 0, AbstractLayout.DEFAULT_VGAP, AbstractLayout.DEFAULT_HGAP));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(getLabel("Domain:"), gridbag.nextLine().next().weightx(0.2));
        centerPanel.add(domain, gridbag.next().weightx(0.8));
        centerPanel.add(getLabel("Domain owner:"), gridbag.nextLine().next().weightx(0.8));
        centerPanel.add(domainOwner, gridbag.next().weightx(0.8));
        centerPanel.add(getLabel("Maven: server id:"), gridbag.nextLine().next().weightx(0.8));
        centerPanel.add(mavenServerId, gridbag.next().weightx(0.8));
        centerPanel.add(getLabel("Maven: settings file:"), gridbag.nextLine().next().weightx(0.8));
        centerPanel.add(mavenSettingsFile, gridbag.next().weightx(0.8));

        PropertiesComponent properties = PropertiesComponent.getInstance();
        domain.setText(getDomain());
        domainOwner.setText(getDomainOwner());
        mavenServerId.setText(getMavenServerId());
        mavenSettingsFile.setText(getMavenServerSettingsFile());

        return centerPanel;
    }


    private JComponent getLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        label.setBorder(JBUI.Borders.empty(0, 5, 2, 0));
        return label;
    }

    @Override
    protected void doOKAction() {
        String mavenSettingsFileText = getGuiValueAndPersist(mavenSettingsFile, "mavenSettingsFile");
        String mavenServerIdText = getGuiValueAndPersist(mavenServerId, "mavenServerId");
        String domainOwnerText = getGuiValueAndPersist(domainOwner, "domainOwner");
        String domainText = getGuiValueAndPersist(domain, "domain");
        if (domainText!=null && domainOwnerText!=null && mavenServerIdText!=null && mavenSettingsFileText!=null){
            super.doOKAction();
        }
    }

    private String getGuiValueAndPersist(JTextField check, String name){
        String ret = check.getText().trim();
        if (ret.isEmpty()) {
            domain.requestFocus();
            return null;
        } else {
            properties.setValue(String.format("%s.%s", PROPERTIES_PREFIX, name), ret);
        }
        return ret;
    }

    private String getPropertiesValue(String name){
        return properties.getValue(String.format("%s.%s", PROPERTIES_PREFIX, name), "");
    }

}