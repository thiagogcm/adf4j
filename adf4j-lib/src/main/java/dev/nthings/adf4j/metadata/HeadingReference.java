package dev.nthings.adf4j.metadata;

/**
 * One document heading in outline order: its clamped level (1–6), plain text, and the anchor the
 * rendered output targets — an explicit Confluence anchor when present, else a document-unique slug.
 */
public record HeadingReference(int level, String text, String anchor) {}
