package net.coderazzi.aws_codeartifact_maven.internal.maven;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.coderazzi.aws_codeartifact_maven.internal.OperationOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class MavenSettingsManager {

    public static final String SERVERS_TAGNAME = "servers";
    public static final String SERVER_TAGNAME = "server";
    public static final String USERNAME_TAGNAME = "username";
    public static final String ID_TAGNAME = "id";

    private MavenSettingsManager() {
        // Prevent instantiation
    }

    public static Optional<String> findServerIdByUsername(String settingsPath, String username) {
        try {
            var xmlFile = new File(settingsPath);
            var docBuilder = getDocumentBuilder();
            Document doc;

            if (!xmlFile.exists()) {
                return Optional.empty();
            }

            doc = docBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            // Get the servers element
            var serversList = doc.getElementsByTagName(SERVERS_TAGNAME);
            if (serversList.getLength() > 0) {
                var serversElement = (Element) serversList.item(0);
                var serverList = serversElement.getElementsByTagName(SERVER_TAGNAME);

                for (var i = 0; i < serverList.getLength(); i++) {
                    var server = (Element) serverList.item(i);
                    var serverUsername = server.getElementsByTagName(USERNAME_TAGNAME).item(0).getTextContent();
                    if (serverUsername.equals(username)) {
                        return Optional.of(server.getElementsByTagName(ID_TAGNAME).item(0).getTextContent());
                    }
                }
            }
        } catch (Exception ignore) {
            // Ignore exceptions
        }
        return Optional.empty();
    }

    public static OperationOutput updateServerCredentials(String settingsPath, String serverId, String username, String password) {
        try {
            var xmlFile = new File(settingsPath);

            var doc = getDocumentOrCreate(xmlFile);
            var serversElement = getServersElementOrCreate(doc);
            var serverElement = getServerElementByServerId(serverId, serversElement);

            if (serverElement == null) {
                addServer(serverId, username, password, doc, serversElement, xmlFile);
            } else {
                updatePassword(password, serverElement, xmlFile);
            }

            return new OperationOutput(true, null);
        } catch (Exception e) {
            return new OperationOutput(false, e.getMessage());
        }
    }

    private static void addServer(String serverId, String username, String password, Document doc, Element serversElement, File xmlFile) throws TransformerException {
        var serverElement = doc.createElement(SERVER_TAGNAME);
        var idElement = doc.createElement(ID_TAGNAME);
        var usernameElement = doc.createElement(USERNAME_TAGNAME);
        var passwordElement = doc.createElement("password");

        idElement.setTextContent(serverId);
        usernameElement.setTextContent(username);
        passwordElement.setTextContent(password);

        serverElement.appendChild(idElement);
        serverElement.appendChild(usernameElement);
        serverElement.appendChild(passwordElement);
        serversElement.appendChild(serverElement);

        updateXmlFile(serversElement, xmlFile);
    }

    private static void updatePassword(String password, Element serverElement, File xmlFile) throws TransformerException {
        var passwordElement = serverElement.getElementsByTagName("password").item(0);
        passwordElement.setTextContent(password);

        updateXmlFile(passwordElement, xmlFile);
    }

    private static void updateXmlFile(Node element, File xmlFile) throws TransformerException {
        var transformer = getTransformer();
        var result = new StreamResult(xmlFile);
        transformer.transform(new DOMSource(element.getOwnerDocument()), result);
    }

    static @Nullable Element getServerElementByServerId(String serverId, Element serversElement) {
        var serverList = serversElement.getElementsByTagName(SERVER_TAGNAME);
        Element serverElement = null;
        for (var i = 0; i < serverList.getLength(); i++) {
            var server = (Element) serverList.item(i);
            if (server.getElementsByTagName(ID_TAGNAME).item(0).getTextContent().equals(serverId)) {
                serverElement = server;
                break;
            }
        }
        return serverElement;
    }

    private static Element getServersElementOrCreate(Document doc) {
        // Get the servers element or create it if it doesn't exist
        var serversList = doc.getElementsByTagName(SERVERS_TAGNAME);
        Element serversElement;
        if (serversList.getLength() == 0) {
            serversElement = doc.createElement(SERVERS_TAGNAME);
            doc.getDocumentElement().appendChild(serversElement);
        } else {
            serversElement = (Element) serversList.item(0);
        }
        return serversElement;
    }

    static @NotNull Document getDocumentOrCreate(File xmlFile) throws ParserConfigurationException, SAXException, IOException {
        var docBuilder = getDocumentBuilder();
        Document doc;

        if (!xmlFile.exists()) {
            // Create a new XML file if it doesn't exist
            doc = docBuilder.newDocument();
            var rootElement = doc.createElement("settings");
            rootElement.setAttribute("xmlns", "http://maven.apache.org/SETTINGS/1.0.0");
            rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            rootElement.setAttribute("xsi:schemaLocation", "http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd");
            doc.appendChild(rootElement);
        } else {
            // Parse the existing XML file
            doc = docBuilder.parse(xmlFile);
        }

        // Normalize the XML structure
        doc.getDocumentElement().normalize();

        return doc;
    }

    private static @NotNull Transformer getTransformer() throws TransformerConfigurationException {
        var transformerFactory = TransformerFactory.newInstance();
        return transformerFactory.newTransformer();
    }

    private static @NotNull DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }
}
