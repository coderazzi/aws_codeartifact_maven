package net.coderazzi.aws_codeartifact_maven;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

class InputDialog extends DialogWrapper {

    private final static String PROPERTIES_PREFIX="net.coderazzi.aws_codeartifact_maven";
    public static final String MAVEN_SETTINGS_FILE = "mavenSettingsFile";
    public static final String AWS_PATH = "awsPath";
    public static final String MAVEN_SERVER_ID = "mavenServerId";
    public static final String DOMAIN_OWNER = "domainOwner";
    public static final String DOMAIN = "domain";
    public static final String DEFAULT_AWS_CLI_PATH = "aws";

    private static String DARK_ICON = "META-INF/pluginIcon_dark.svg";
    private static String LIGHT_ICON = "META-INF/pluginIcon.svg";

    private final JTextField domain = new JTextField(32);
    private final JTextField domainOwner = new JTextField(32);
    private final JTextField mavenServerId = new JTextField(32);
    private final JTextField mavenSettingsFile = new JTextField(32);
    private final JTextField awsPath = new JTextField(32);

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
        return getPropertiesValue(DOMAIN);
    }

    public String getDomainOwner(){
        return getPropertiesValue(DOMAIN_OWNER);
    }

    public String getMavenServerId(){
        return getPropertiesValue(MAVEN_SERVER_ID);
    }

    public String getAWSPath(){
        String ret =  getPropertiesValue(AWS_PATH);
        return ret.trim().isEmpty()? DEFAULT_AWS_CLI_PATH : ret;
    }

    public String getMavenServerSettingsFile(){
        String ret = getPropertiesValue(MAVEN_SETTINGS_FILE);
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
        centerPanel.add(getLabel("AWS cli path:"), gridbag.nextLine().next().weightx(0.8));
        centerPanel.add(awsPath, gridbag.next().weightx(0.8));

        PropertiesComponent properties = PropertiesComponent.getInstance();
        domain.setText(getDomain());
        domainOwner.setText(getDomainOwner());
        mavenServerId.setText(getMavenServerId());
        mavenSettingsFile.setText(getMavenServerSettingsFile());
        awsPath.setText(getAWSPath());

        JPanel ret = new JPanel(new BorderLayout(24, 0));
        ret.add(centerPanel, BorderLayout.CENTER);
        ret.add(getIconPanel(), BorderLayout.WEST);

        return ret;
    }

    private JComponent getIconPanel(){
        String resource = ColorUtil.isDark(getOwner().getBackground())? DARK_ICON : LIGHT_ICON;
        InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
        try {
            return new JLabel(new ImageIcon(SVGLoader.load(is, 2.0f)));
        } catch (IOException ex){
            return new JLabel();
        } finally{
            try {is.close();} catch(IOException ex) {}
        }
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
        getGuiValueAndPersist(awsPath, AWS_PATH);
        String mavenSettingsFileText = getGuiValueAndPersist(mavenSettingsFile, MAVEN_SETTINGS_FILE);
        String mavenServerIdText = getGuiValueAndPersist(mavenServerId, MAVEN_SERVER_ID);
        String domainOwnerText = getGuiValueAndPersist(domainOwner, DOMAIN_OWNER);
        String domainText = getGuiValueAndPersist(domain, DOMAIN);
        if (domainText!=null && domainOwnerText!=null && mavenServerIdText!=null && mavenSettingsFileText!=null){
            super.doOKAction();
        }
    }

    private String getGuiValueAndPersist(JTextField check, String name){
        String ret = check.getText().trim();
        setPropertiesValue(name, ret);
        if (ret.isEmpty()) {
            domain.requestFocus();
            return null;
        }
        return ret;
    }

    private void setPropertiesValue(String name, String value){
        properties.setValue(String.format("%s.%s", PROPERTIES_PREFIX, name), value);
    }

    private String getPropertiesValue(String name){
        return properties.getValue(String.format("%s.%s", PROPERTIES_PREFIX, name), "");
    }

}