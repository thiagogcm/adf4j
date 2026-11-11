package dev.nthings.adf4j.ast;

/// Node types the ADF schema allows directly inside `blockTaskItem`. {@link Adf} accepts only
/// these; parsed documents may hold any child. Node types only, not marks, position, count or
/// attributes. The schema constrains only the first two children; this holds every child to that
/// rule, so it is stricter.
public sealed interface BlockTaskItemContent extends AdfBlock permits Extension, Paragraph {}
