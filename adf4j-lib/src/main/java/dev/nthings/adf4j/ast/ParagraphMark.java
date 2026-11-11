package dev.nthings.adf4j.ast;

/// Marks the ADF schema allows on a {@link Paragraph}. Which apply depends on the container;
/// alignment and indentation are mutually exclusive, not enforced here.
public sealed interface ParagraphMark extends AdfMark permits Alignment, FontSize, Indentation {}
