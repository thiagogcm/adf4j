package dev.nthings.adf4j.internal.render.markdown;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.render.AdfDocumentRenderer;
import dev.nthings.adf4j.internal.render.RenderEnvironment;
import dev.nthings.adf4j.options.ConversionOptions;
import org.jspecify.annotations.Nullable;

/// Turns a document into a Markdown body; diagnostics and unresolved lookups go to the events.
/// Stateless: each call gets a fresh {@link RenderSession}.
public final class MarkdownAdfRenderer {

  private final BlockRenderer blockRenderer;

  private MarkdownAdfRenderer(BlockRenderer blockRenderer) {
    this.blockRenderer = blockRenderer;
  }

  /// Keeps the CommonMark wiring out of callers.
  public static MarkdownAdfRenderer createDefault() {
    return new MarkdownAdfRenderer(new BlockRenderer(CommonMarkSupport.cellHtmlRenderer()));
  }

  /// Returns an {@link AdfDocumentRenderer} bound to `options`.
  public AdfDocumentRenderer forOptions(ConversionOptions options) {
    return (document, environment) -> render(document, environment, options);
  }

  private String render(
      AdfDocument document, RenderEnvironment environment, ConversionOptions options) {
    var session = RenderSession.from(environment, options);
    var body = blockRenderer.renderBlocks(document, new RenderContext(session, InlineSite.BODY));
    return prependTitle(body, environment.context().title(), session.writer());
  }

  private static String prependTitle(
      String body, @Nullable String rawTitle, MarkdownWriter writer) {
    var heading = titleHeading(rawTitle, writer);
    if (heading == null) {
      return body;
    }
    return body.isEmpty() ? heading : heading + "\n\n" + body;
  }

  // Like renderHeading's level-1 case: one line, punctuation escaped (not at line start).
  private static @Nullable String titleHeading(@Nullable String rawTitle, MarkdownWriter writer) {
    if (rawTitle == null) {
      return null;
    }
    var oneLine = Strings.collapseLineBreaks(rawTitle).strip();
    if (oneLine.isEmpty()) {
      return null;
    }
    return "# " + writer.text(oneLine, false);
  }
}
