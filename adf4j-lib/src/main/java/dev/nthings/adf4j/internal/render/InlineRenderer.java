package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfCard;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfUnknown;
import dev.nthings.adf4j.ast.Code;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.result.AdfException;
import dev.nthings.adf4j.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Renders inline nodes to one string, tracking line starts and spacing images in headings.
final class InlineRenderer {

  private static final Logger log = LoggerFactory.getLogger(InlineRenderer.class);

  private final TextMarkRenderer markRenderer;
  private final MediaRenderer mediaRenderer;
  private final MacroRenderer macroRenderer;
  private final CardRenderer cardRenderer;

  InlineRenderer(
      TextMarkRenderer markRenderer,
      MediaRenderer mediaRenderer,
      MacroRenderer macroRenderer,
      CardRenderer cardRenderer) {
    this.markRenderer = markRenderer;
    this.mediaRenderer = mediaRenderer;
    this.macroRenderer = macroRenderer;
    this.cardRenderer = cardRenderer;
  }

  /// `startAtLineStart` enables leading-block escaping at output column 0 (paragraphs, captions),
  /// not mid-line (headings, task items). Later nodes get their real column, whitespace-only
  /// inlines included. HEADING and GFM_CELL are one line, so nothing in them starts a line. In a
  /// heading an image gets a space from adjacent text (`![icon](src) Title`).
  String renderInlineNodes(
      List<Located<AdfInline>> nodes, RenderContext context, boolean startAtLineStart) {
    var inHeading = context.site() == InlineSite.HEADING;
    var hasLines = !inHeading && context.site() != InlineSite.GFM_CELL;
    var builder = new StringBuilder();
    var atLineStart = hasLines && startAtLineStart;
    @Nullable AdfInline previous = null;
    nodes.forEach(node -> context.session().events().observe(node.node(), node.path()));
    for (var located : coalesceAdjacentText(nodes, context.session())) {
      var node = located.node();
      var rendered = renderInline(node, context.at(located), atLineStart);
      if (rendered.isEmpty()) {
        continue;
      }
      if (inHeading && needsImageSeparator(previous, node, builder, rendered)) {
        builder.append(' ');
      }
      builder.append(rendered);
      previous = node;
      atLineStart = hasLines && MarkdownText.endsAtLineStart(builder, startAtLineStart);
    }
    return builder.toString();
  }

  /// Merges adjacent {@link Text} runs that render alike, so they share one set of delimiters.
  /// A merged run keeps its first pointer.
  private List<Located<AdfInline>> coalesceAdjacentText(
      List<Located<AdfInline>> nodes, RenderSession session) {
    var result = new ArrayList<Located<AdfInline>>(nodes.size());
    for (var located : nodes) {
      if (located.node() instanceof Text text
          && !result.isEmpty()
          && result.getLast().node() instanceof Text last
          && markRenderer.rendersAlike(last.marks(), text.marks(), session)) {
        result.set(
            result.size() - 1,
            new Located<>(
                new Text(last.text() + text.text(), last.marks()), result.getLast().path()));
      } else {
        result.add(located);
      }
    }
    return result;
  }

  private String renderInline(AdfInline node, RenderContext context, boolean atLineStart) {
    var writer = context.session().writer();
    return switch (node) {
      case Text text -> renderText(text, context, atLineStart);
      case HardBreak _ -> hardBreakMarker(context);
      case AdfCard card -> cardRenderer.renderInlineCard(card, context);
      case MediaInline media -> mediaRenderer.renderMedia(media, context);
      // Attribute text escapes like literal text.
      case Date date -> writer.text(AdfText.dateFromTimestamp(date.timestamp()), atLineStart);
      case Emoji emoji -> writer.text(AdfText.emojiLabel(emoji), atLineStart);
      case Mention mention -> writer.text(AdfText.mentionLabel(mention), atLineStart);
      case Placeholder placeholder -> writer.text(placeholder.text(), atLineStart);
      case Status status -> writer.label(AdfText.statusLabel(status));
      case InlineExtension extension ->
          macroRenderer.renderExtension(extension, context, atLineStart);
      case UnknownInline unknown -> renderUnknown(unknown, context);
    };
  }

  private static boolean needsImageSeparator(
      @Nullable AdfInline previous, AdfInline current, StringBuilder builder, String rendered) {
    if (!(previous instanceof MediaInline) && !(current instanceof MediaInline)) {
      return false;
    }
    if (builder.isEmpty()) {
      return false;
    }
    return !Character.isWhitespace(builder.charAt(builder.length() - 1))
        && !Character.isWhitespace(rendered.charAt(0));
  }

  private String renderText(Text text, RenderContext context, boolean atLineStart) {
    // Code is literal (applyMarks wraps it in backticks), so it is not escaped.
    var hasCodeMark = text.marks().stream().anyMatch(Code.class::isInstance);
    if (hasCodeMark) {
      return markRenderer.applyMarks(text.text(), text.marks(), context);
    }
    var escaped = context.session().writer().text(text.text(), atLineStart);
    return markRenderer.applyMarks(escaped, text.marks(), context);
  }

  private String hardBreakMarker(RenderContext context) {
    return switch (context.site()) {
      // A break would end the ATX heading.
      case HEADING -> " ";
      // Cells: one "\n" (a <br> after unwrap), not the two-space form.
      case GFM_CELL, HTML_CELL -> "\n";
      // collapseHardBreaks turns the two-space break into a plain newline.
      case BODY -> context.session().options().collapseHardBreaks() ? "\n" : "  \n";
    };
  }

  // Block and inline unknowns share one policy; only wording and raw form differ.
  String renderUnknown(AdfUnknown node, RenderContext context) {
    var isBlock = node instanceof UnknownBlock;
    var kind = isBlock ? "block" : "inline";
    var nodeType = node.type();
    var label = nodeType.isBlank() ? "<empty>" : nodeType;
    return switch (context.session().options().unknownNodePolicy()) {
      case SKIP -> {
        log.warn("Skipping unsupported ADF {} node type: {}", kind, label);
        yield "";
      }
      case FAIL ->
          throw new AdfException(
              List.of(
                  new Diagnostic(Diagnostic.Code.UNKNOWN_NODE_REJECTED, "Unknown node: " + label)
                      .at(context.path())));
      case PRESERVE_RAW -> {
        log.warn("Preserving raw JSON for unsupported ADF {} node type: {}", kind, label);
        var rawJson = node.rawJson();
        if (rawJson.isBlank()) {
          yield "";
        }
        yield isBlock
            ? MarkdownText.fencedCodeBlock(rawJson, "json")
            : MarkdownText.inlineCodeSpan(rawJson);
      }
      case PLACEHOLDER -> {
        log.warn("Rendering placeholder for unsupported ADF {} node type: {}", kind, label);
        yield context.session().writer().label(AdfText.unsupportedLabel(node));
      }
    };
  }
}
