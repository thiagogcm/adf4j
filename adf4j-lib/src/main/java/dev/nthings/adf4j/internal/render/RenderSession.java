package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.ConversionOptions;

/// Per-render state shared by the traversal: outline, options, attachments, the per-render sinks,
/// resolvers, and the writer holding the escaping options.
record RenderSession(
    HeadingOutline headingOutline,
    ConversionOptions options,
    AttachmentInventory attachments,
    MacroDiagnostics macroDiagnostics,
    Resolvers resolvers,
    MarkdownWriter writer,
    RenderEvents events) {

  static RenderSession from(
      ConversionOptions options, AttachmentInventory attachments, HeadingOutline headingOutline) {
    var events = new RenderEvents(options);
    return new RenderSession(
        headingOutline,
        options,
        attachments,
        new MacroDiagnostics(),
        new Resolvers(options, events),
        new MarkdownWriter(options.escapeParentheses()),
        events);
  }
}
