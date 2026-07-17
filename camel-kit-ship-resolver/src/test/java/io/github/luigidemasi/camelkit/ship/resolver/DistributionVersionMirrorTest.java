package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributionVersionMirrorTest {

    private static final String BEGIN_MARKER = "BEGIN distribution property mirrors";
    private static final String END_MARKER = "END distribution property mirrors";
    private static final Set<String> REQUIRED_RESOLVER_MIRRORS = Set.of(
            "maven.resolver.version",
            "maven.provider.version",
            "maven.jar.plugin.version",
            "maven.shade.plugin.version",
            "maven.surefire.plugin.version",
            "slf4j.version");

    @Test
    void mavenModelLiteralsMatchPackagedDistributionPolicy()
            throws IOException, ParserConfigurationException, SAXException {
        Path root = projectRoot();
        Map<String, String> pomValues = mirroredPomProperties(root.resolve("pom.xml"));
        assertTrue(
                pomValues.keySet().containsAll(REQUIRED_RESOLVER_MIRRORS),
                "Root POM distribution property mirror block lacks a required resolver version");

        Properties distribution = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve("distribution.properties"))) {
            distribution.load(reader);
        }
        for (Map.Entry<String, String> entry : pomValues.entrySet()) {
            assertEquals(distribution.getProperty(entry.getKey()), entry.getValue(), entry.getKey());
        }
    }

    private static Map<String, String> mirroredPomProperties(Path pom)
            throws IOException, ParserConfigurationException, SAXException {
        Document document;
        try (InputStream input = Files.newInputStream(pom)) {
            document = secureDocumentBuilderFactory().newDocumentBuilder().parse(input);
        }
        assertEquals("project", localName(document.getDocumentElement()), "Root POM lacks the project element");
        Element properties = directChild(document.getDocumentElement(), "properties");

        Map<String, String> values = new LinkedHashMap<>();
        boolean inside = false;
        boolean foundBegin = false;
        boolean foundEnd = false;
        for (Node node = properties.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.COMMENT_NODE) {
                String marker = node.getNodeValue().trim();
                if (BEGIN_MARKER.equals(marker) || marker.startsWith(BEGIN_MARKER + '\n')) {
                    assertFalse(foundBegin, "Root POM contains more than one distribution property mirror block");
                    assertFalse(foundEnd, "Root POM distribution property mirror block starts after its end");
                    foundBegin = true;
                    inside = true;
                    continue;
                }
                if (END_MARKER.equals(marker)) {
                    assertTrue(inside, "Root POM distribution property mirror block ends before it starts");
                    assertFalse(foundEnd, "Root POM contains more than one distribution property mirror block");
                    foundEnd = true;
                    inside = false;
                    continue;
                }
            }
            if (inside && node.getNodeType() == Node.ELEMENT_NODE) {
                String name = localName(node);
                assertNull(values.put(name, node.getTextContent().trim()), name);
            }
        }
        assertTrue(foundBegin, "Root POM lacks the marked distribution property mirror block");
        assertTrue(foundEnd, "Root POM distribution property mirror block has no end marker");
        assertFalse(inside, "Root POM distribution property mirror block is not closed");
        assertFalse(values.isEmpty(), "Root POM distribution property mirror block is empty");
        return values;
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setIgnoringComments(false);
        return factory;
    }

    private static Element directChild(Element parent, String name) {
        Element found = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(localName(node))) {
                assertNull(found, "Root POM contains more than one direct " + name + " element");
                found = (Element) node;
            }
        }
        assertNotNull(found, "Root POM lacks a direct " + name + " element");
        return found;
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private static Path projectRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("distribution.properties"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new AssertionError("Could not locate the project root from " + Path.of("").toAbsolutePath());
    }
}
