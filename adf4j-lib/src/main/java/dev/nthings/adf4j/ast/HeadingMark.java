package dev.nthings.adf4j.ast;

/// The marks the ADF schema can allow on a {@link Heading}. `AdfContentModelTests` checks this
/// against the schema. Which of them apply depends on where the heading is, and alignment and
/// indentation exclude each other, which this doesn't cover.
public sealed interface HeadingMark extends AdfMark permits Alignment, Indentation {}
