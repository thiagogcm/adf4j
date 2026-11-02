package dev.nthings.adf4j.ast;

/// The marks the ADF schema allows on {@link Media}. `AdfContentModelTests` checks this against the
/// schema.
public sealed interface MediaMark extends AdfMark permits Annotation, Border, DataConsumer, Link {}
