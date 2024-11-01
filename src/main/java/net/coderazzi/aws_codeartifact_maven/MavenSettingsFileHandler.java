package net.coderazzi.aws_codeartifact_maven;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class MavenSettingsFileHandler {

    static class GetServerIdsException extends Exception {
        GetServerIdsException(String ex) {
            super(ex);
        }
    }

    public static final String PASSWORD = "password";

    private final String settingsPath;
    private Element xmlPasswordElement;

    public MavenSettingsFileHandler(String settingsPath) {
        this.settingsPath = settingsPath;
    }

    public Set<String> getServerIds(String username) throws GetServerIdsException {
        Set<String> ret = new TreeSet<>();
        try {
            Document input = getDocument();
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xpath.evaluate("/settings/servers/server", input, XPathConstants.NODESET);
            for (int i = nodes.getLength() - 1; i >= 0; i--) {
                Node node = nodes.item(i);
                Element element = (Element) node;
                NodeList idsNodes = element.getElementsByTagName("id");
                NodeList usernameNodes = element.getElementsByTagName("username");
                if (idsNodes.getLength() == 1 && usernameNodes.getLength() == 1 && username.equals(usernameNodes.item(0).getTextContent())) {
                    ret.add(idsNodes.item(0).getTextContent());
                }
            }
        } catch (ParserConfigurationException | SAXException | XPathExpressionException ex) {
            throw new GetServerIdsException(String.format("XML parsing error in settings file %s: %s", this.settingsPath, ex.getMessage()));
        } catch (IOException ex) {
            throw new GetServerIdsException(String.format("Error accessing settings file: %s", ex.getMessage()));
        }
        return ret;
    }

    private Document getDocument() throws ParserConfigurationException, SAXException, IOException {
        File f = new File(this.settingsPath);
        if (f.canRead()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            //            dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return dbFactory.newDocumentBuilder().parse(f);
        }
        if (f.exists()) {
            throw new IOException(String.format("File '%s' does not exist", this.settingsPath));
        }
        throw new IOException(String.format("Cannot read file '%s'", this.settingsPath));
    }

    public OperationOutput locateServer(String serverId) {
        OperationOutput ret = new OperationOutput();
        xmlPasswordElement = null;
        try {
            Document input = getDocument();
            XPath xpath = XPathFactory.newInstance().newXPath();
            String expr = String.format("/settings/servers/server/id[text()=\"%s\"]", serverId);
            NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
            if (nodes.getLength() == 1) {
                Element parent = (Element) nodes.item(0).getParentNode();
                nodes = parent.getElementsByTagName(PASSWORD);
                if (nodes.getLength() == 1) {
                    xmlPasswordElement = (Element) nodes.item(0);
                    ret.ok = true;
                } else if (nodes.getLength() == 0) {
                    xmlPasswordElement = input.createElement(PASSWORD);
                    parent.appendChild(xmlPasswordElement);
                    ret.ok = true;
                } else {
                    ret.output = String.format("Unexpected: many password tags for server '%s' in settings file %s",
                            serverId, this.settingsPath);
                }
            } else {
                ret.output = String.format("Cannot find a server '%s' in settings file '%s'",
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

    public OperationOutput setPassword(String awsCredential) {//throws TransformerException, IOException {
        OperationOutput ret = new OperationOutput();
        if (xmlPasswordElement == null) {
            ret.output = "Cannot replace auth token";
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
            } catch (TransformerException tex) {
                ret.output = "Unexpected XML error: " + tex.getMessage();
            } catch (IOException ex) {
                ret.output = "Could not update settings file: " + ex.getMessage();
            }
        }
        return ret;
    }
}
