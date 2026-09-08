package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.confluence.ConfluenceRenderContext;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
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
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The traversal cursor: a shared immutable {@link RenderContext} plus the position-dependent state
/// that changes as the renderer descends (table scope, heading scope). Transitions copy only the
/// cursor fields and keep the same {@link RenderContext} reference.
record RendererState(RenderContext context, TableCellKind tableCell, boolean inHeading) {

  static RendererState root(MarkdownOptions options, HeadingOutline headingOutline) {
    return new RendererState(
        RenderContext.from(options, headingOutline), TableCellKind.NONE, false);
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

  @Nullable MediaResolver mediaResolver() {
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

  @Nullable AttachmentResolver attachmentResolver() {
    return context.options().attachmentResolver();
  }

  @Nullable PageTreeResolver pageTreeResolver() {
    return context.options().pageTreeResolver();
  }

  @Nullable ExcerptResolver excerptResolver() {
    return context.options().excerptResolver();
  }

  void recordUnsupportedExtension(@Nullable String extensionType, @Nullable String extensionKey) {
    context.macroDiagnostics().recordUnsupported(extensionType, extensionKey);
  }

  UnresolvedTracker unresolvedTracker() {
    return context.unresolvedTracker();
  }

  List<Diagnostic> macroDiagnostics() {
    return context.macroDiagnostics().build();
  }

  @Nullable HeadingReference headingInfo(Heading heading) {
    return context.headingOutline().infoFor(heading);
  }

  boolean isTocReferenced(Heading heading) {
    return context.headingOutline().isTocReferenced(heading);
  }

  // Cursor transitions.
  RendererState withTableCell(TableCellKind cell) {
    return new RendererState(context, cell, inHeading);
  }

  RendererState withHeading(boolean heading) {
    return new RendererState(context, tableCell, heading);
  }
}
