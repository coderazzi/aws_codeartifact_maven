package net.coderazzi.aws_codeartifact_maven.internal.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class MavenSettingsManagerTest {

    private static final String TEST_SETTINGS_PATH = "test-settings.xml";
    private static final String TEST_SETTINGS_PATH2 = "test-settings2.xml";

    @BeforeEach
    void setUp() throws IOException {
        var xmlContent = """
                <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
                    <servers>
                        <server>
                            <id>codeartifact-server</id>
                            <username>aws</username>
                            <password>pw</password>
                        </server>
                        <server>
                            <id>another-server</id>
                            <username>otheruser</username>
                            <password>otherpw</password>
                        </server>
                    </servers>
                </settings>
                """;

        Files.write(Paths.get(TEST_SETTINGS_PATH), xmlContent.getBytes());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_SETTINGS_PATH));
        Files.deleteIfExists(Paths.get(TEST_SETTINGS_PATH2));
    }

    @Test
    void testFindServerIdByUsernameFound() {
        var serverId = MavenSettingsManager.findServerIdByUsername(TEST_SETTINGS_PATH, "aws");
        assertTrue(serverId.isPresent());
        assertEquals("codeartifact-server", serverId.get());
    }

    @Test
    void testFindServerIdByUsernameNotFound() {
        var serverId = MavenSettingsManager.findServerIdByUsername(TEST_SETTINGS_PATH, "nonexistent");
        assertFalse(serverId.isPresent());
    }

    @Test
    void testUpdateServerCredentialsNewServer() {
        var output = MavenSettingsManager.updateServerCredentials(TEST_SETTINGS_PATH, "new-server", "newuser", "newpassword");
        assertTrue(output.success());

        var serverId = MavenSettingsManager.findServerIdByUsername(TEST_SETTINGS_PATH, "newuser");
        assertTrue(serverId.isPresent());
        assertEquals("new-server", serverId.get());
    }

    @Test
    void testUpdateServerCredentialsExistingServer() throws ParserConfigurationException, IOException, SAXException {
        var output = MavenSettingsManager.updateServerCredentials(TEST_SETTINGS_PATH, "codeartifact-server", "aws", "newpassword");
        assertTrue(output.success());

        var doc = MavenSettingsManager.getDocumentOrCreate(new File(TEST_SETTINGS_PATH));
        var serverElement = MavenSettingsManager.getServerElementByServerId("codeartifact-server", doc.getDocumentElement());
        assertNotNull(serverElement);
        var password = serverElement.getElementsByTagName("password").item(0).getTextContent();
        assertEquals("newpassword", password);
    }

    @Test
    void testUpdateServerCredentialsFileNotExistYes() throws ParserConfigurationException, IOException, SAXException {
        var output = MavenSettingsManager.updateServerCredentials(TEST_SETTINGS_PATH2, "codeartifact-server", "user", "pass");

        assertTrue(output.success());
        assertTrue(Files.exists(Paths.get(TEST_SETTINGS_PATH2)));
        var doc = MavenSettingsManager.getDocumentOrCreate(new File(TEST_SETTINGS_PATH2));
        var serverElement = MavenSettingsManager.getServerElementByServerId("codeartifact-server", doc.getDocumentElement());
        assertNotNull(serverElement);
        var password = serverElement.getElementsByTagName("password").item(0).getTextContent();
        assertEquals("pass", password);
    }
}
