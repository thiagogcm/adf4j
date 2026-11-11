package dev.nthings.adf4j.ast;

/// Marks the ADF schema allows on {@link Media}.
public sealed interface MediaMark extends AdfMark permits Annotation, Border, DataConsumer, Link {}
