package dev.nthings.adf4j.renderer;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.nthings.adf4j.AttachmentReference;
import dev.nthings.adf4j.HeadingReference;
import dev.nthings.adf4j.PageTitleResolver;
import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.model.ExcerptKey;
import dev.nthings.adf4j.model.MacroContext;
import dev.nthings.adf4j.model.PageLinkResolver;
import dev.nthings.adf4j.model.UnknownNodePolicy;

/**
 * The traversal cursor: a shared immutable {@link RenderContext} plus the position-dependent state
 * that changes as the renderer descends (list depth, table scope, active excerpts). Transitions
 * copy only the cursor fields and keep the same {@link RenderContext} reference.
 */
record RendererState(
    RenderContext context,
    int listDepth,
    boolean inTable,
    Set<ExcerptKey> activeExcerpts) {

  static RendererState root(RenderOptions options, HeadingOutline headingOutline) {
    return new RendererState(RenderContext.from(options, headingOutline), 0, false, Set.of());
  }

  // Delegating accessors onto the shared context so renderer call sites stay stable.
  String pageTitle() {
    return context.pageTitle();
  }

  String currentPageId() {
    return context.currentPageId();
  }

  MacroContext macroContext() {
    return context.macroContext();
  }

  Map<String, AttachmentReference> attachmentReferencesByTitle() {
    return context.attachmentReferencesByTitle();
  }

  PageLinkResolver linkResolver() {
    return context.linkResolver();
  }

  PageTitleResolver pageTitleResolver() {
    return context.pageTitleResolver();
  }

  UnknownNodePolicy unknownNodePolicy() {
    return context.unknownNodePolicy();
  }

  HeadingReference headingInfo(Heading heading) {
    return context.headingOutline().infoFor(heading);
  }

  // Cursor transitions.
  RendererState withListDepth(int depth) {
    return new RendererState(context, depth, inTable, activeExcerpts);
  }

  RendererState withTable(boolean table) {
    return new RendererState(context, listDepth, table, activeExcerpts);
  }

  boolean isExcerptActive(ExcerptKey key) {
    return activeExcerpts.contains(key);
  }

  RendererState withExcerpt(ExcerptKey key) {
    var next = new LinkedHashSet<>(activeExcerpts);
    next.add(key);
    return new RendererState(context, listDepth, inTable, Set.copyOf(next));
  }
}
