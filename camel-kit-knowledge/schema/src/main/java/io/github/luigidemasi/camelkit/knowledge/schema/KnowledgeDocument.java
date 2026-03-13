package io.github.luigidemasi.camelkit.knowledge.schema;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.TextField;

/**
 * Builder for Lucene documents that enforces the knowledge schema.
 * Ensures correct field types (StringField for exact match, TextField for search).
 */
public class KnowledgeDocument {

    private final Document doc = new Document();

    public KnowledgeDocument(String id, String domain) {
        doc.add(new StringField(KnowledgeFields.ID, id, Store.YES));
        doc.add(new StringField(KnowledgeFields.DOMAIN, domain, Store.YES));
    }

    public KnowledgeDocument source(String source) {
        doc.add(new StringField(KnowledgeFields.SOURCE, source, Store.YES));
        return this;
    }

    public KnowledgeDocument docType(String docType) {
        doc.add(new StringField(KnowledgeFields.DOC_TYPE, docType, Store.YES));
        return this;
    }

    public KnowledgeDocument sourceVersion(String version) {
        doc.add(new StringField(KnowledgeFields.SOURCE_VERSION, version, Store.YES));
        return this;
    }

    public KnowledgeDocument targetVersion(String version) {
        doc.add(new StringField(KnowledgeFields.TARGET_VERSION, version, Store.YES));
        return this;
    }

    public KnowledgeDocument component(String component) {
        doc.add(new StringField(KnowledgeFields.COMPONENT, component, Store.YES));
        return this;
    }

    public KnowledgeDocument sectionTitle(String title) {
        doc.add(new TextField(KnowledgeFields.SECTION_TITLE, title, Store.YES));
        return this;
    }

    public KnowledgeDocument content(String content) {
        doc.add(new TextField(KnowledgeFields.CONTENT, content, Store.YES));
        return this;
    }

    /** Adds a single runtime. Call multiple times for multi-valued field. */
    public KnowledgeDocument runtime(String runtime) {
        doc.add(new StringField(KnowledgeFields.RUNTIME, runtime, Store.YES));
        return this;
    }

    /** Adds a single JIRA ID. Call multiple times for multi-valued field. */
    public KnowledgeDocument jiraId(String jiraId) {
        doc.add(new StringField(KnowledgeFields.JIRA_ID, jiraId, Store.YES));
        return this;
    }

    public KnowledgeDocument erratumId(String erratumId) {
        doc.add(new StringField(KnowledgeFields.ERRATUM_ID, erratumId, Store.YES));
        return this;
    }

    public KnowledgeDocument advisoryType(String advisoryType) {
        doc.add(new StringField(KnowledgeFields.ADVISORY_TYPE, advisoryType, Store.YES));
        return this;
    }

    public KnowledgeDocument severity(String severity) {
        doc.add(new StringField(KnowledgeFields.SEVERITY, severity, Store.YES));
        return this;
    }

    /** Adds a single CVE ID. Call multiple times for multi-valued field. */
    public KnowledgeDocument cveId(String cveId) {
        doc.add(new StringField(KnowledgeFields.CVE_IDS, cveId, Store.YES));
        return this;
    }

    /** Adds a single fixed-in version. Call multiple times for multi-valued field. */
    public KnowledgeDocument fixedInVersion(String version) {
        doc.add(new StringField(KnowledgeFields.FIXED_IN_VERSIONS, version, Store.YES));
        return this;
    }

    public KnowledgeDocument embedding(float[] vector) {
        doc.add(new KnnFloatVectorField(KnowledgeFields.EMBEDDING, vector));
        return this;
    }

    public KnowledgeDocument domainMeta(String json) {
        doc.add(new StringField(KnowledgeFields.DOMAIN_META, json, Store.YES));
        return this;
    }

    public Document build() {
        return doc;
    }
}
