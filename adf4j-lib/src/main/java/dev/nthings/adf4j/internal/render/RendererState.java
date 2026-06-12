package dev.nthings.adf4j.internal.render;

import java.util.List;

import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.ExcerptResolver;
import dev.nthings.adf4j.options.ExtensionRenderer;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageTreeResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.result.Diagnostic;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;

/**
 * The traversal cursor: a shared immutable {@link RenderContext} plus the position-dependent state
 * that changes as the renderer descends (list depth, table scope). Transitions copy only the cursor
 * fields and keep the same {@link RenderContext} reference.
 */
record RendererState(RenderContext context, int listDepth, TableCellKind tableCell, boolean inHeading) {

  static RendererState root(MarkdownOptions options, HeadingOutline headingOutline) {
    return new RendererState(RenderContext.from(options, headingOutline), 0, TableCellKind.NONE, false);
  }

  List<HeadingReference> headings() {
    return context.headingOutline().headings();
  }

  ConfluenceRenderContext confluenceContext() {
    return context.options().confluenceContext();
  }

  UnknownNodePolicy unknownNodePolicy() {
    return context.options().unknownNodePolicy();
  }

  boolean imageSizeAttributes() {
    return context.options().imageSizeAttributes();
  }

  TableFallback tableFallback() {
    return context.options().tableFallback();
  }

  MediaResolver mediaResolver() {
    return context.options().mediaResolver();
  }

  boolean htmlVisualMarks() {
    return context.options().htmlVisualMarks();
  }

  boolean collapseHardBreaks() {
    return context.options().collapseHardBreaks();
  }

  boolean escapeParentheses() {
    return context.options().escapeParentheses();
  }

  List<ExtensionRenderer> extensionRenderers() {
    return context.options().extensionRenderers();
  }

  AttachmentResolver attachmentResolver() {
    return context.options().attachmentResolver();
  }

  PageTreeResolver pageTreeResolver() {
    return context.options().pageTreeResolver();
  }

  ExcerptResolver excerptResolver() {
    return context.options().excerptResolver();
  }

  void recordUnsupportedExtension(String extensionType, String extensionKey) {
    context.macroDiagnostics().recordUnsupported(extensionType, extensionKey);
  }

  UnresolvedTracker unresolvedTracker() {
    return context.unresolvedTracker();
  }

  List<Diagnostic> macroDiagnostics() {
    return context.macroDiagnostics().build();
  }

  HeadingReference headingInfo(Heading heading) {
    return context.headingOutline().infoFor(heading);
  }

  boolean isTocReferenced(Heading heading) {
    return context.headingOutline().isTocReferenced(heading);
  }

  // Cursor transitions.
  RendererState withListDepth(int depth) {
    return new RendererState(context, depth, tableCell, inHeading);
  }

  RendererState withTableCell(TableCellKind cell) {
    return new RendererState(context, listDepth, cell, inHeading);
  }

  RendererState withHeading(boolean heading) {
    return new RendererState(context, listDepth, tableCell, heading);
  }
}
