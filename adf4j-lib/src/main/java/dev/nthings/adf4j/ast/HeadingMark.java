package dev.nthings.adf4j.ast;

/// Marks the ADF schema allows on a {@link Heading}. Which apply depends on where the heading is;
/// alignment and indentation exclude each other, which this type does not enforce.
public sealed interface HeadingMark extends AdfMark permits Alignment, Indentation {}
