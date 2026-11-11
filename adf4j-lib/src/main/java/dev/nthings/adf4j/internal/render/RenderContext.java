package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfNode;

record RenderContext(RenderSession session, InlineSite site, String path) {
  RenderContext(RenderSession session, InlineSite site) {
    this(session, site, "");
  }

  RenderContext withSite(InlineSite value) {
    return new RenderContext(session, value, path);
  }

  RenderContext at(AdfNode node) {
    var found = session.events().source.path(node);
    return new RenderContext(session, site, found.isEmpty() ? path : found);
  }
}
