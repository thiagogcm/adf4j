package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.ConversionOptions;

/// Per-render state shared by the whole traversal: the precomputed outline, the conversion's
/// {@link ConversionOptions} and the {@link AttachmentInventory} built from them, the two mutable
/// per-render sinks, the {@link Resolvers} that feed one of them, and the {@link MarkdownWriter}
/// holding the escaping options. The moving part lives in {@link RenderContext}.
record RenderSession(
    HeadingOutline headingOutline,
    ConversionOptions options,
    AttachmentInventory attachments,
    MacroDiagnostics macroDiagnostics,
    UnresolvedTracker unresolvedTracker,
    Resolvers resolvers,
    MarkdownWriter writer,
    RenderEvents events) {

  static RenderSession from(
      ConversionOptions options,
      AttachmentInventory attachments,
      HeadingOutline headingOutline,
      AdfDocument document) {
    var unresolved = new UnresolvedTracker();
    var events = new RenderEvents(document, options);
    return new RenderSession(
        headingOutline,
        options,
        attachments,
        new MacroDiagnostics(),
        unresolved,
        new Resolvers(options, events),
        new MarkdownWriter(options.escapeParentheses()),
        events);
  }
}
