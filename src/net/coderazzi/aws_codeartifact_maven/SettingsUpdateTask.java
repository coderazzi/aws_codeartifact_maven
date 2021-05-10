package net.coderazzi.aws_codeartifact_maven;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class SettingsUpdateTask {

    public static final String PASSWORD = "password";

    private final String settingsPath;
    private Element xmlPasswordElement;

    public SettingsUpdateTask(String settingsPath){
        this.settingsPath = settingsPath;
    }

    public TaskOutput locateServer(String serverId) {
        TaskOutput ret = new TaskOutput();
        xmlPasswordElement = null;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//            dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document input = dbFactory.newDocumentBuilder().parse(this.settingsPath);
            XPath xpath = XPathFactory.newInstance().newXPath();
            String expr = String.format("/settings/servers/server/id[text()=\"%s\"]", serverId);
            NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
            if (nodes.getLength() == 1) {
                Element parent = (Element) nodes.item(0).getParentNode();
                nodes = parent.getElementsByTagName(PASSWORD);
                if (nodes.getLength() == 1) {
                    xmlPasswordElement = (Element) nodes.item(0);
                    ret.ok=true;
                } else if (nodes.getLength() == 0) {
                    xmlPasswordElement = input.createElement(PASSWORD);
                    parent.appendChild(xmlPasswordElement);
                    ret.ok=true;
                } else {
                    ret.output = String.format("Unexpected: many password tags for server '%s' in settings file %s",
                            serverId, this.settingsPath);
                }
            } else {
                ret.output = String.format("Cannot find a server '%s' in settings file",
                        serverId, this.settingsPath);
            }
        } catch (ParserConfigurationException | SAXException ex) {
            ret.output = String.format("XML parsing error in settings file %s: %s", this.settingsPath, ex.getMessage());
        } catch (XPathExpressionException ex) {
            ret.output = String.format("XPath error, invalid maven server id(%s)", serverId);
        } catch (IOException ex) {
            ret.output = String.format("Error accessing settings file: %s", ex.getMessage());
        }
        return ret;
    }

    public TaskOutput setPassword(String awsCredential) {//throws TransformerException, IOException {
        TaskOutput ret = new TaskOutput();
        if (xmlPasswordElement==null){
            ret.output = "Cannot replace credentials";
        } else {
            xmlPasswordElement.setTextContent(awsCredential);

            TransformerFactory tFactory = TransformerFactory.newInstance();
            try {
//                tFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                Transformer transformer = tFactory.newTransformer();

                File settings = new File(this.settingsPath);
                File temp = File.createTempFile("settings-tmp-", ".xml", settings.getParentFile());
                temp.deleteOnExit();
                try {
                    StreamResult output = new StreamResult(temp);
                    transformer.transform(new DOMSource(xmlPasswordElement.getOwnerDocument()), output);
                    Files.move(temp.toPath(), settings.toPath(), REPLACE_EXISTING);
                    ret.ok = true;
                } finally {
                    temp.delete();
                }
            } catch (TransformerException tex){
                ret.output = "Unexpected XML error: " + tex.getMessage();
            } catch (IOException ex){
                ret.output = "Could not update settings file: " + ex.getMessage();
            }
        }
        return ret;
    }
}
