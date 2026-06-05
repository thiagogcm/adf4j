package dev.nthings.adf4j.internal.render;

import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.extension.ExtensionRenderer;
import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.AttachmentResolver;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.MediaResolver;
import dev.nthings.adf4j.options.PageLinkResolver;
import dev.nthings.adf4j.options.TableFallback;
import dev.nthings.adf4j.options.UnknownNodePolicy;
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

  // Delegating accessors onto the shared context so renderer call sites stay stable.
  List<HeadingReference> headings() {
    return context.headingOutline().headings();
  }

  Map<String, AttachmentReference> attachmentReferencesByTitle() {
    return context.attachmentReferencesByTitle();
  }

  UnknownNodePolicy unknownNodePolicy() {
    return context.unknownNodePolicy();
  }

  boolean imageSizeAttributes() {
    return context.imageSizeAttributes();
  }

  TableFallback tableFallback() {
    return context.tableFallback();
  }

  MediaResolver mediaResolver() {
    return context.mediaResolver();
  }

  boolean htmlVisualMarks() {
    return context.htmlVisualMarks();
  }

  List<ExtensionRenderer> extensionRenderers() {
    return context.extensionRenderers();
  }

  AttachmentResolver attachmentResolver() {
    return context.attachmentResolver();
  }

  PageLinkResolver pageLinkResolver() {
    return context.pageLinkResolver();
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
