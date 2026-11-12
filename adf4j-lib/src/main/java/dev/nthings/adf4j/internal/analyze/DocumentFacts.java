package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.metadata.ContentMetadata;

/// Document references and heading facts, separate from rendering diagnostics. Heading labels
/// follow the supplied unknown-node policy; collection follows {@link AdfNodeWalker}'s interpreted
/// content rather than every preserved AST child.
public record DocumentFacts(HeadingOutline outline, ContentMetadata metadata) {}
