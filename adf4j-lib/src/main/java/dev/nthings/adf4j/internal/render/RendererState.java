package dev.nthings.adf4j.internal.render;

import java.util.List;
import java.util.Map;

import dev.nthings.adf4j.metadata.AttachmentReference;
import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import dev.nthings.adf4j.ast.Heading;

/**
 * The traversal cursor: a shared immutable {@link RenderContext} plus the position-dependent state
 * that changes as the renderer descends (list depth, table scope). Transitions copy only the cursor
 * fields and keep the same {@link RenderContext} reference.
 */
record RendererState(RenderContext context, int listDepth, boolean inTable, boolean inHeading) {

  static RendererState root(MarkdownOptions options, HeadingOutline headingOutline) {
    return new RendererState(RenderContext.from(options, headingOutline), 0, false, false);
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

  HeadingReference headingInfo(Heading heading) {
    return context.headingOutline().infoFor(heading);
  }

  // Cursor transitions.
  RendererState withListDepth(int depth) {
    return new RendererState(context, depth, inTable, inHeading);
  }

  RendererState withTable(boolean table) {
    return new RendererState(context, listDepth, table, inHeading);
  }

  RendererState withHeading(boolean heading) {
    return new RendererState(context, listDepth, inTable, heading);
  }
}
