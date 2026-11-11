package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;

/// Reacts to the nodes of an ADF document during one {@link AdfNodeWalker} pass; descent into a
/// node's children is the walker's job, so a collector never re-implements the tree-walk.
interface NodeVisitor {

  /// Called once for every block in document order, before the walker descends into its children.
  /// `path` is the block's JSON Pointer.
  default void visitBlock(AdfBlock block, String path) {}

  /// Called once for every inline in document order. `path` is the inline's JSON Pointer.
  default void visitInline(AdfInline inline, String path) {}
}
