package dev.nthings.adf4j.metadata;

/** A distinct Confluence page the document links to, by the page node id its links carry. */
public record PageReference(String pageNodeId) {}
