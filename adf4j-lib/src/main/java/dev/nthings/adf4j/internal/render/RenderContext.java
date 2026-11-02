package dev.nthings.adf4j.internal.render;

/// Where the traversal is: the shared {@link RenderSession} plus the {@link InlineSite} that
/// changes as the renderer descends. Transitions copy only the site and keep the same session.
record RenderContext(RenderSession session, InlineSite site) {

  RenderContext withSite(InlineSite newSite) {
    return new RenderContext(session, newSite);
  }
}
