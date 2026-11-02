package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.MarkdownOptions;

/// Per-render state shared by the whole traversal: the precomputed outline, the conversion's
/// {@link MarkdownOptions} and the {@link AttachmentInventory} built from them, the two mutable
/// per-render sinks, the {@link Resolvers} that feed one of them, and the {@link MarkdownWriter}
/// holding the escaping options. The moving part lives in {@link RenderContext}.
record RenderSession(
    HeadingOutline headingOutline,
    MarkdownOptions options,
    AttachmentInventory attachments,
    MacroDiagnostics macroDiagnostics,
    UnresolvedTracker unresolvedTracker,
    Resolvers resolvers,
    MarkdownWriter writer) {

  static RenderSession from(
      MarkdownOptions options, AttachmentInventory attachments, HeadingOutline headingOutline) {
    var unresolved = new UnresolvedTracker();
    return new RenderSession(
        headingOutline,
        options,
        attachments,
        new MacroDiagnostics(),
        unresolved,
        new Resolvers(options, unresolved),
        new MarkdownWriter(options.escapeParentheses()));
  }
}
