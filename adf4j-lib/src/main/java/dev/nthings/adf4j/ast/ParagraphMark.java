package dev.nthings.adf4j.ast;

/// The marks the ADF schema can allow on a {@link Paragraph}. `AdfContentModelTests` checks this
/// against the schema. Which of them apply depends on where the paragraph is, and alignment and
/// indentation exclude each other, which this doesn't cover.
public sealed interface ParagraphMark extends AdfMark permits Alignment, FontSize, Indentation {}
