package dev.nthings.adf4j.ast;

/// Root of the ADF AST: a document, a block, or an inline. The sealed hierarchies under this root
/// *grow* as ADF evolves (new permitted types may be added in any release), so a consumer
/// `switch` should keep a `default` (or handle the `Unknown*` variants) rather than
/// rely on exhaustiveness over today's types.
public sealed interface AdfNode permits AdfDocument, AdfBlock, AdfInline {}
