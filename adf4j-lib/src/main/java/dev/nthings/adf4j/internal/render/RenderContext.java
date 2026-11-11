package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.internal.Located;
import java.util.List;

/// Traversal position: output site and the JSON Pointer of the node. Renderers descend with
/// {@link #children} and {@link #at}, so pointers follow source position.
record RenderContext(RenderSession session, InlineSite site, String path) {
  RenderContext(RenderSession session, InlineSite site) {
    this(session, site, "");
  }

  RenderContext withSite(InlineSite value) {
    return new RenderContext(session, value, path);
  }

  RenderContext at(Located<?> child) {
    return new RenderContext(session, site, child.path());
  }

  /// The `type` children of `parent`, which this context sits at.
  <T extends AdfNode> List<Located<T>> children(AdfNode parent, Class<T> type) {
    return Located.children(parent, path, type);
  }
}
