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
import dev.nthings.adf4j.result.UnknownNodeException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Renders inline nodes to one string: coalesces adjacent text, tracks whether the next node sits
/// at a line start, and spaces images apart inside a heading.
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

  /// Renders `nodes` in order. `startAtLineStart` enables leading-block escaping for inlines at
  /// output column 0 (paragraphs, a list item's first paragraph, a caption); false for mid-line
  /// text (headings, task/decision items). Every later node gets the position it really lands on,
  /// whitespace-only inlines included. {@link InlineSite#HEADING} and {@link InlineSite#GFM_CELL}
  /// are one output line, so nothing in them starts a line. A heading also keeps a hard break on
  /// its line and inserts one space between an inline image and adjacent text so they don't glue
  /// (e.g. `![icon](src) Title`); all-text headings are unaffected.
  String renderInlineNodes(List<AdfInline> nodes, RenderContext context, boolean startAtLineStart) {
    var inHeading = context.site() == InlineSite.HEADING;
    var hasLines = !inHeading && context.site() != InlineSite.GFM_CELL;
    var builder = new StringBuilder();
    var atLineStart = hasLines && startAtLineStart;
    @Nullable AdfInline previous = null;
    for (var node : coalesceAdjacentText(nodes, context.session())) {
      var rendered = renderInline(node, context, atLineStart);
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

  /// Merges consecutive {@link Text} inlines whose marks render identically into one node, so an
  /// adjacent run gets a single set of delimiters instead of one per node. Non-Text inlines break a
  /// run.
  private List<AdfInline> coalesceAdjacentText(List<AdfInline> nodes, RenderSession session) {
    var result = new ArrayList<AdfInline>(nodes.size());
    for (var node : nodes) {
      if (node instanceof Text text
          && !result.isEmpty()
          && result.getLast() instanceof Text last
          && markRenderer.rendersAlike(last.marks(), text.marks(), session)) {
        result.set(result.size() - 1, new Text(last.text() + text.text(), last.marks()));
      } else {
        result.add(node);
      }
    }
    return result;
  }

  private String renderInline(AdfInline node, RenderContext context, boolean atLineStart) {
    var writer = context.session().writer();
    return switch (node) {
      case Text text -> renderText(text, context, atLineStart);
      case HardBreak _ -> hardBreakMarker(context);
      case AdfCard card -> cardRenderer.renderInlineCard(card, context.session());
      case MediaInline media -> mediaRenderer.renderMedia(media, context);
      // Attribute-derived text is escaped like literal text, honouring atLineStart.
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
    // Code-marked text is literal (applyMarks wraps it in backticks), so it must not be escaped.
    var hasCodeMark = text.marks().stream().anyMatch(Code.class::isInstance);
    if (hasCodeMark) {
      return markRenderer.applyMarks(text.text(), text.marks(), context.session());
    }
    var escaped = context.session().writer().text(text.text(), atLineStart);
    return markRenderer.applyMarks(escaped, text.marks(), context.session());
  }

  private String hardBreakMarker(RenderContext context) {
    return switch (context.site()) {
      // An interior break would terminate an ATX heading, so collapse it to a space.
      case HEADING -> " ";
      // In any table cell a break is a single "\n" (one <br> after unwrap), not the two-space form.
      case GFM_CELL, HTML_CELL -> "\n";
      // collapseHardBreaks downgrades the two-space GFM hard break to a soft break (a plain
      // newline).
      case BODY -> context.session().options().collapseHardBreaks() ? "\n" : "  \n";
    };
  }

  // One policy for both kinds of unknown node, which differ only in wording and raw form.
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
          throw new UnknownNodeException(
              nodeType, "Unsupported ADF " + kind + " node type: " + label);
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
