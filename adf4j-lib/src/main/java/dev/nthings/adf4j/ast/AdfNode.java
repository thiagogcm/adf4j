package dev.nthings.adf4j.ast;

/**
 * Root of the ADF AST: a document, a block, or an inline. The sealed hierarchies under this root
 * <em>grow</em> as ADF evolves — new permitted types may be added in any release — so a consumer
 * {@code switch} should keep a {@code default} (or handle the {@code Unknown*} variants) rather than
 * rely on exhaustiveness over today's types.
 */
public sealed interface AdfNode permits AdfDocument, AdfBlock, AdfInline {}
