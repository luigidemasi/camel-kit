package io.github.luigidemasi.camelkit.generator;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsoncObjectEditorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Timeout(5)
    void rejectsUnterminatedBlockComment() {
        assertThrows(IllegalArgumentException.class,
                () -> new JsoncObjectEditor("{ /* never closed\n  \"a\": 1 }"));
    }

    @Test
    void rejectsTruncatedObject() {
        assertThrows(IllegalArgumentException.class, () -> new JsoncObjectEditor("{\"a\": "));
        assertThrows(IllegalArgumentException.class, () -> new JsoncObjectEditor("{\"a\": {\"b\": 1"));
    }

    @Test
    void expandedValuesKeepTheMemberIndentation() {
        JsoncObjectEditor editor = new JsoncObjectEditor("{\n  \"permission\": \"deny\",\n  \"mcp\": {}\n}\n");
        ObjectNode expanded = JSON.createObjectNode();
        expanded.put("*", "deny");

        editor.replaceRootMemberValue("permission", expanded);

        assertEquals("{\n  \"permission\": {\n    \"*\" : \"deny\"\n  },\n  \"mcp\": {}\n}\n", editor.content());
    }

    @Test
    void removingAMemberRemovesItsWholeLine() {
        JsoncObjectEditor editor = new JsoncObjectEditor(
                "{\n  \"permission\": {\n    \"custom\": \"deny\",\n    \"camel_*\": \"allow\"\n  }\n}\n");

        editor.removeObjectMembers("permission", List.of("camel_*").iterator());

        assertEquals("{\n  \"permission\": {\n    \"custom\": \"deny\"\n  }\n}\n", editor.content());
    }

    @Test
    void removingMembersThatShareALineRemovesTheLine() {
        JsoncObjectEditor editor = new JsoncObjectEditor(
                "{\n  \"permission\": {\n    \"camel_a\": \"allow\", /* c */ \"camel_b\": \"allow\",\n"
                                                         + "    \"custom\": \"deny\"\n  }\n}\n");

        editor.removeObjectMembers("permission", List.of("camel_a", "camel_b").iterator());

        assertEquals("{\n  \"permission\": {\n    /* c */\n    \"custom\": \"deny\"\n  }\n}\n", editor.content());
    }

    @Test
    void removingAMemberKeepsItsCommentsOnTheirOwnLine() {
        JsoncObjectEditor editor = new JsoncObjectEditor(
                "{\n  \"permission\": {\n    \"camel_*\": /* keep */ \"allow\",\n    \"custom\": \"deny\"\n  }\n}\n");

        editor.removeObjectMembers("permission", List.of("camel_*").iterator());

        assertEquals("{\n  \"permission\": {\n    /* keep */\n    \"custom\": \"deny\"\n  }\n}\n", editor.content());
    }
}
