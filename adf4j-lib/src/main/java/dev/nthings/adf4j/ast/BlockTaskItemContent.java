package dev.nthings.adf4j.ast;

/// The node types the ADF schema allows directly inside `blockTaskItem`. Records hold any child, so
/// that every parsed document fits; this names the types for code that constructs documents. It
/// covers node types only, not the marks, position, count or attributes a child also needs. The
/// schema constrains only the first two children of a `blockTaskItem`; this holds every child to
/// that rule, so it is stricter than the schema. `AdfContentModelTests` checks it against the
/// schema.
public sealed interface BlockTaskItemContent extends AdfBlock permits Extension, Paragraph {}
