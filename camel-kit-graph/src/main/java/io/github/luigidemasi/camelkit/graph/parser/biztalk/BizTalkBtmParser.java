package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

/**
 * Parser for BizTalk BTM (map) files.
 * <p>
 * BTM files are XML files that define transformations between BizTalk schemas using functoids (transformation
 * functions) and links (wiring between schema elements and functoids).
 * </p>
 */
public class BizTalkBtmParser {

    /**
     * Mapping from Functoid FID to functoid type name. This provides a fallback when FunctoidType attribute is not
     * present.
     */
    private static final Map<Integer, String> FID_TO_TYPE = Map.ofEntries(
            // String functoids (101-110)
            Map.entry(101, "String"),
            Map.entry(102, "String"),
            Map.entry(103, "String"),
            Map.entry(104, "String"),
            Map.entry(105, "String"),
            Map.entry(106, "String"),
            Map.entry(107, "String"), // Concatenate
            Map.entry(108, "String"),
            Map.entry(109, "String"),
            Map.entry(110, "String"),
            // Math functoids (111-121)
            Map.entry(111, "Math"), // Add
            Map.entry(112, "Math"), // Subtract
            Map.entry(113, "Math"), // Multiply
            Map.entry(114, "Math"), // Divide
            Map.entry(115, "Math"), // Modulo
            Map.entry(116, "Math"), // Abs
            Map.entry(117, "Math"), // Max
            Map.entry(118, "Math"), // Min
            Map.entry(119, "Math"), // Round
            Map.entry(120, "Math"), // Floor
            Map.entry(121, "Math"), // Ceiling
            // Date functoids (122-125)
            Map.entry(122, "Date"),
            Map.entry(123, "Date"),
            Map.entry(124, "Date"),
            Map.entry(125, "Date"),
            // Scripting functoid (260)
            Map.entry(260, "Scripting"),
            // Logical functoids (311-321, 701, 705-706)
            Map.entry(311, "Logical"),
            Map.entry(312, "Logical"),
            Map.entry(313, "Logical"),
            Map.entry(314, "Logical"),
            Map.entry(315, "Logical"),
            Map.entry(316, "Logical"),
            Map.entry(317, "Logical"),
            Map.entry(318, "Logical"),
            Map.entry(319, "Logical"),
            Map.entry(320, "Logical"),
            Map.entry(321, "Logical"),
            Map.entry(701, "Logical"),
            Map.entry(705, "Logical"),
            Map.entry(706, "Logical"),
            // Looping functoids (424-425)
            Map.entry(424, "Looping"),
            Map.entry(425, "Looping"),
            // Advanced functoids (800-802)
            Map.entry(800, "Advanced"), // MassCopy
            Map.entry(801, "Advanced"),
            Map.entry(802, "Advanced"));

    /**
     * Parse a single BTM file and add nodes/edges to the graph.
     *
     * @param btmFile the BTM file to parse
     * @param graph   the project graph to populate
     */
    public void parse(Path btmFile, ProjectGraph graph) {
        try {
            if (!Files.exists(btmFile)) {
                return;
            }

            parseBtmXml(btmFile, graph);
        } catch (Exception e) {
            // Silently skip unparseable files
        }
    }

    /**
     * Parse BTM XML content using StAX.
     */
    private void parseBtmXml(Path btmFile, ProjectGraph graph) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (InputStream input = Files.newInputStream(btmFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);

            try {
                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT && "mapsource".equals(reader.getLocalName())) {
                        parseMapSource(reader, btmFile, graph);
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    /**
     * Parse the mapsource root element.
     */
    private void parseMapSource(XMLStreamReader reader, Path btmFile, ProjectGraph graph) throws Exception {
        // Extract map name from filename (strip .btm extension)
        String fileName = btmFile.getFileName().toString();
        String mapName = fileName.endsWith(".btm") ? fileName.substring(0, fileName.length() - 4) : fileName;

        String sourceSchema = null;
        String targetSchema = null;
        int functoidCount = 0;

        Map<String, String> functoidData = new HashMap<>(); // FunctoidID -> mapId:functoid:FunctoidId

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("SrcTree".equals(localName)) {
                    sourceSchema = reader.getAttributeValue(null, "Schema");
                } else if ("TrgTree".equals(localName)) {
                    targetSchema = reader.getAttributeValue(null, "Schema");
                } else if ("Functoid".equals(localName)) {
                    parseFunctoid(reader, mapName, graph, functoidData);
                    functoidCount++;
                    // parseFunctoid consumes the entire Functoid element including closing tag
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("mapsource".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        // Create map node
        String mapId = "biztalk-map:" + mapName;
        Map<String, String> mapProps = new HashMap<>();
        mapProps.put("file", btmFile.toString());
        mapProps.put("name", mapName);
        if (sourceSchema != null) {
            mapProps.put("sourceSchema", sourceSchema);
        }
        if (targetSchema != null) {
            mapProps.put("targetSchema", targetSchema);
        }
        mapProps.put("functoidCount", String.valueOf(functoidCount));

        GraphNode mapNode = new GraphNode(mapId, NodeType.BIZTALK_MAP, mapProps);
        graph.addNode(mapNode);

        // Create schema nodes and edges
        if (sourceSchema != null) {
            String sourceSchemaId = "biztalk-schema:" + sourceSchema;
            GraphNode sourceSchemaNode = new GraphNode(sourceSchemaId, NodeType.BIZTALK_SCHEMA, Map.of());
            graph.addNode(sourceSchemaNode);

            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("role", "source");
            GraphEdge sourceEdge = new GraphEdge(mapId, sourceSchemaId, EdgeType.BIZTALK_USES_SCHEMA, edgeProps);
            graph.addEdge(sourceEdge);
        }

        if (targetSchema != null) {
            String targetSchemaId = "biztalk-schema:" + targetSchema;
            GraphNode targetSchemaNode = new GraphNode(targetSchemaId, NodeType.BIZTALK_SCHEMA, Map.of());
            graph.addNode(targetSchemaNode);

            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("role", "target");
            GraphEdge targetEdge = new GraphEdge(mapId, targetSchemaId, EdgeType.BIZTALK_USES_SCHEMA, edgeProps);
            graph.addEdge(targetEdge);
        }

        // Create edges from map to functoids
        for (String functoidId : functoidData.values()) {
            GraphEdge edge = new GraphEdge(mapId, functoidId, EdgeType.BIZTALK_FUNCTOID_CHAIN, Map.of());
            graph.addEdge(edge);
        }
    }

    /**
     * Parse a Functoid element.
     */
    private void parseFunctoid(
            XMLStreamReader reader, String mapName, ProjectGraph graph,
            Map<String, String> functoidData)
            throws Exception {
        String functoidId = reader.getAttributeValue(null, "FunctoidID");
        String fidStr = reader.getAttributeValue(null, "Functoid-FID");
        String functoidTypeAttr = reader.getAttributeValue(null, "FunctoidType");

        if (functoidId == null) {
            skipElement(reader);
            return;
        }

        // Determine functoid type: prefer explicit FunctoidType attribute, fall back to FID lookup
        String functoidType = functoidTypeAttr;
        if (functoidType == null && fidStr != null) {
            try {
                int fid = Integer.parseInt(fidStr);
                functoidType = FID_TO_TYPE.get(fid);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Look for ScripterCode to detect script language
        String scriptLanguage = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("ScripterCode".equals(localName)) {
                    scriptLanguage = reader.getAttributeValue(null, "Language");
                }
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        // Create functoid node
        String nodeId = "biztalk-map:" + mapName + ":functoid:" + functoidId;
        Map<String, String> props = new HashMap<>();
        if (functoidType != null) {
            props.put("functoidType", functoidType);
        }
        if (fidStr != null) {
            props.put("functoidFid", fidStr);
        }
        if (scriptLanguage != null) {
            props.put("scriptLanguage", scriptLanguage);
        }

        GraphNode functoidNode = new GraphNode(nodeId, NodeType.BIZTALK_FUNCTOID, props);
        graph.addNode(functoidNode);

        functoidData.put(functoidId, nodeId);
    }

    /**
     * Skip the current element and all its children.
     */
    private void skipElement(XMLStreamReader reader) throws Exception {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
