package dev.nthings.adf4j.internal.render.markdown;

import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.internal.render.RenderEnvironment;
import dev.nthings.adf4j.internal.render.RenderEvents;
import dev.nthings.adf4j.internal.resource.ResourceResolver;
import dev.nthings.adf4j.options.ConversionOptions;
import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.options.UnknownNodePolicy;
import java.util.function.Predicate;

/// Per-render Markdown state wrapping the shared {@link RenderEnvironment} with Markdown policy,
/// Markdown-valued hooks, and the escaping writer.
record RenderSession(
    RenderEnvironment environment,
    MarkdownOptions markdown,
    MarkdownHooks hooks,
    MarkdownWriter writer) {
  private static final Predicate<AdfMark> OMITS_WITH_HTML =
      mark -> TextMarkRenderer.omitsVisual(mark, true);
  private static final Predicate<AdfMark> OMITS_PLAIN =
      mark -> TextMarkRenderer.omitsVisual(mark, false);

  static RenderSession from(RenderEnvironment environment, ConversionOptions options) {
    return new RenderSession(
        environment,
        options.markdown(),
        new MarkdownHooks(options.resolvers(), environment.events().callbacks()),
        new MarkdownWriter(options.escapeParentheses()));
  }

  /// Records the marks of `node` that Markdown loses, including visual marks it omits.
  void observeMarks(AdfNode node, String path) {
    events().observeMarks(node, path, markdown.htmlVisualMarks() ? OMITS_WITH_HTML : OMITS_PLAIN);
  }

  HeadingOutline headingOutline() {
    return environment.headingOutline();
  }

  UnknownNodePolicy unknownNodePolicy() {
    return environment.unknownNodePolicy();
  }

  AttachmentInventory attachments() {
    return environment.attachments();
  }

  ResourceResolver resources() {
    return environment.resources();
  }

  RenderEvents events() {
    return environment.events();
  }
}
