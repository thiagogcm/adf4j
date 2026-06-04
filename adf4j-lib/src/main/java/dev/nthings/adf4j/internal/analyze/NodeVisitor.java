package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;

/**
 * Reacts to the nodes of an ADF document during one {@link AdfNodeWalker} pass; descent into a node's
 * children is the walker's job, so a collector never re-implements the tree-walk.
 */
interface NodeVisitor {

  // Called once for every block in document order, before the walker descends into its children.
  default void visitBlock(AdfBlock block) {
  }

  // Called once for every inline in document order.
  default void visitInline(AdfInline inline) {
  }
}
