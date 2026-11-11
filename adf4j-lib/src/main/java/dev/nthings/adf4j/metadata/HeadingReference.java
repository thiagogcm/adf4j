package dev.nthings.adf4j.metadata;

/// A heading in outline order. `level` is clamped to 1-6; `anchor` is the rendered link target,
/// an explicit Confluence anchor or else a document-unique slug.
public record HeadingReference(int level, String text, String anchor) {}
