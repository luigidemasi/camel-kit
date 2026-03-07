package io.github.luigidemasi.camelkit.knowledge.schema;

import org.apache.lucene.document.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeDocumentTest {

    @Test
    void buildMigrationDocument() {
        Document doc = new KnowledgeDocument("test-id", "camel_migration")
                .source("apache-camel")
                .docType("component-migration")
                .sourceVersion("2.x")
                .targetVersion("4.x")
                .component("camel-cxf")
                .sectionTitle("camel-cxf refactored")
                .content("The camel-cxf component has been refactored in 4.x.")
                .build();

        assertEquals("test-id", doc.get(KnowledgeFields.ID));
        assertEquals("camel_migration", doc.get(KnowledgeFields.DOMAIN));
        assertEquals("apache-camel", doc.get(KnowledgeFields.SOURCE));
        assertEquals("component-migration", doc.get(KnowledgeFields.DOC_TYPE));
        assertEquals("2.x", doc.get(KnowledgeFields.SOURCE_VERSION));
        assertEquals("4.x", doc.get(KnowledgeFields.TARGET_VERSION));
        assertEquals("camel-cxf", doc.get(KnowledgeFields.COMPONENT));
        assertEquals("camel-cxf refactored", doc.get(KnowledgeFields.SECTION_TITLE));
        assertEquals("The camel-cxf component has been refactored in 4.x.", doc.get(KnowledgeFields.CONTENT));
    }

    @Test
    void domainMetadataMigrationFactory() {
        DomainMetadata meta = DomainMetadata.migration(
                "rh_fuse_migration",
                "camel_rh_fuse_migration",
                "Search Red Hat Fuse migration docs"
        );

        assertEquals("rh_fuse_migration", meta.domainId());
        assertEquals("camel_rh_fuse_migration", meta.toolName());
        assertTrue(meta.hasComponentField());
        assertTrue(meta.hasVersionFields());
    }

    @Test
    void domainMetadataGeneralFactory() {
        DomainMetadata meta = DomainMetadata.general(
                "best_practices",
                "camel_best_practices",
                "Search Camel best practices"
        );

        assertFalse(meta.hasComponentField());
        assertFalse(meta.hasVersionFields());
    }
}
