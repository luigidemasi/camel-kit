package io.github.luigidemasi.camelkit.graph.model;

public enum EdgeType {
    EXTENDS,
    IMPLEMENTS,
    DECLARES,
    CALLS,
    USES_TYPE,
    ROUTES_FROM,
    ROUTES_TO,
    PROCESSES,
    LINKS_TO,
    DEPENDS_ON,
    USES_COMPONENT,
    CONFIGURES,
    MULE_FLOW_CONTAINS,
    MULE_CALLS_SUBFLOW,
    MULE_USES_CONNECTOR,
    MULE_REFERENCES_DWL
}
