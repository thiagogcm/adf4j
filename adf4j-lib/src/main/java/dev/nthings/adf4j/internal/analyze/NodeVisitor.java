package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;

/// Reacts to nodes during an {@link AdfNodeWalker} pass. The walker owns descent into children.
interface NodeVisitor {

  /// Called for each block in document order, before its children. `path` is its JSON Pointer.
  default void visitBlock(AdfBlock block, String path) {}

  /// Called for each inline in document order. `path` is its JSON Pointer.
  default void visitInline(AdfInline inline, String path) {}
}
