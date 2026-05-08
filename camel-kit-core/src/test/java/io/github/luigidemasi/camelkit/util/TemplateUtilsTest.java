package io.github.luigidemasi.camelkit.util;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateUtilsTest {

    @Test
    void readTemplateReturnsContentForExistingTemplate() throws IOException {
        String content = TemplateUtils.readTemplate("templates/constitution.md");
        assertNotNull(content);
        assertFalse(content.isEmpty());
    }

    @Test
    void readTemplateThrowsTemplateNotFoundForMissing() {
        assertThrows(TemplateNotFoundException.class,
                () -> TemplateUtils.readTemplate("templates/does-not-exist.md"));
    }

    @Test
    void readTemplateOrNullReturnsContentForExistingTemplate() {
        String content = TemplateUtils.readTemplateOrNull("templates/constitution.md");
        assertNotNull(content);
        assertFalse(content.isEmpty());
    }

    @Test
    void readTemplateOrNullReturnsNullForMissingTemplate() {
        assertNull(TemplateUtils.readTemplateOrNull("templates/does-not-exist.md"));
    }
}
