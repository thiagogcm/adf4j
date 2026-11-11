package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.internal.AttachmentInventory;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.options.ConversionOptions;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// The render phase: turns a document into a Markdown body, with the optional title heading, plus
/// the diagnostics and unresolved lookups the render raised. Stateless and thread-safe, since each
/// call gets a fresh {@link RenderSession}.
public final class AdfRenderer {

  private final BlockRenderer blockRenderer;

  private AdfRenderer(BlockRenderer blockRenderer) {
    this.blockRenderer = blockRenderer;
  }

  /// Assembles a renderer with its default delegates, keeping the CommonMark dependency a render
  /// detail.
  public static AdfRenderer createDefault() {
    return new AdfRenderer(new BlockRenderer(CommonMarkSupport.cellHtmlRenderer()));
  }

  public RenderOutput render(
      @Nullable AdfDocument document,
      ConversionOptions options,
      AttachmentInventory attachments,
      HeadingOutline headingOutline) {
    var writer = new MarkdownWriter(options.escapeParentheses());
    if (document == null) {
      return new RenderOutput(
          prependTitle("", options.documentTitle(), writer), List.of(), List.of(), List.of());
    }

    var session = RenderSession.from(options, attachments, headingOutline, document);
    var body =
        blockRenderer.renderBlocks(document.content(), new RenderContext(session, InlineSite.BODY));
    return new RenderOutput(
        prependTitle(body, options.documentTitle(), writer),
        Stream.concat(
                session.macroDiagnostics().build().stream(),
                session.events().allDiagnostics(options.unknownNodePolicy()).stream())
            .toList(),
        session.unresolvedTracker().build(),
        session.events().allEffects());
  }

  // Prepends the optional documentTitle as a level-1 heading, blank-line separated from the body.
  private static String prependTitle(
      String body, @Nullable String rawTitle, MarkdownWriter writer) {
    var heading = titleHeading(rawTitle, writer);
    if (heading == null) {
      return body;
    }
    return body.isEmpty() ? heading : heading + "\n\n" + body;
  }

  // Formats the title like renderHeading's plain level-1 case: collapsed to one line, punctuation
  // escaped (atLineStart=false, since the text follows "# "). Null/blank yields no heading.
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
