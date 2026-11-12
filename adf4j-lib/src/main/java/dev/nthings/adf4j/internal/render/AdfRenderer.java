package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.ConversionOptions;
import org.jspecify.annotations.Nullable;

/// Turns a document into a Markdown body, plus the diagnostics and unresolved lookups raised.
/// Stateless: each call gets a fresh {@link RenderSession}.
public final class AdfRenderer {

  private final BlockRenderer blockRenderer;

  private AdfRenderer(BlockRenderer blockRenderer) {
    this.blockRenderer = blockRenderer;
  }

  /// Keeps the CommonMark wiring out of callers.
  public static AdfRenderer createDefault() {
    return new AdfRenderer(new BlockRenderer(CommonMarkSupport.cellHtmlRenderer()));
  }

  public RenderOutput render(
      AdfDocument document,
      ConversionOptions options,
      AttachmentInventory attachments,
      HeadingOutline headingOutline) {
    var session = RenderSession.from(options, attachments, headingOutline);
    var body = blockRenderer.renderBlocks(document, new RenderContext(session, InlineSite.BODY));
    return new RenderOutput(
        prependTitle(body, options.documentTitle(), session.writer()),
        session.events().diagnostics(),
        session.events().unresolved(),
        session.events().effects());
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
