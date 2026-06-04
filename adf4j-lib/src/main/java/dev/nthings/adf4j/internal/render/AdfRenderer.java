package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import dev.nthings.adf4j.options.MarkdownOptions;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.analyze.HeadingContent;
import dev.nthings.adf4j.internal.analyze.HeadingOutline;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.Alignment;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Code;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.Indentation;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Rule;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.ast.UnknownInline;

import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdfRenderer implements BlockRecursion {

  private static final Logger log = LoggerFactory.getLogger(AdfRenderer.class);

  private final TextMarkRenderer markRenderer;
  private final ListRenderer listRenderer;
  private final TableRenderer tableRenderer;
  private final MediaRenderer mediaRenderer;
  private final MacroRenderer macroRenderer;
  private final CardRenderer cardRenderer;

  public AdfRenderer(
      TextMarkRenderer markRenderer,
      ListRenderer listRenderer,
      TableRenderer tableRenderer,
      MediaRenderer mediaRenderer,
      MacroRenderer macroRenderer,
      CardRenderer cardRenderer) {
    this.markRenderer = markRenderer;
    this.listRenderer = listRenderer;
    this.tableRenderer = tableRenderer;
    this.mediaRenderer = mediaRenderer;
    this.macroRenderer = macroRenderer;
    this.cardRenderer = cardRenderer;
  }

  /** Assembles a renderer with its default delegates, keeping the CommonMark dependency a render detail. */
  public static AdfRenderer createDefault() {
    return new AdfRenderer(
        new TextMarkRenderer(),
        new ListRenderer(),
        new TableRenderer(markdownRenderingSupport()),
        new MediaRenderer(),
        new MacroRenderer(),
        new CardRenderer());
  }

  private static MarkdownRenderingSupport markdownRenderingSupport() {
    var extensions = commonmarkExtensions();
    var parser = Parser.builder().extensions(extensions).build();
    var htmlRenderer = HtmlRenderer.builder().extensions(extensions).sanitizeUrls(false).build();
    return new MarkdownRenderingSupport(parser, htmlRenderer);
  }

  private static List<org.commonmark.Extension> commonmarkExtensions() {
    return List.of(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        HeadingAnchorExtension.create(),
        ImageAttributesExtension.create(),
        AlertsExtension.create());
  }

  public String render(
      AdfDocument document, MarkdownOptions options, HeadingOutline headingOutline) {
    if (document == null) {
      return "";
    }

    var requiredOptions = Objects.requireNonNull(options, "options");
    var outline = headingOutline == null ? HeadingOutline.empty() : headingOutline;
    var context = RendererState.root(requiredOptions, outline);
    return joinBlocks(renderBlocks(document.content(), context));
  }

  @Override
  public List<String> renderBlock(AdfBlock block, RendererState context) {
    return switch (block) {
      case Paragraph paragraph -> List.of(renderParagraph(paragraph, context));
      case Heading heading -> List.of(renderHeading(heading, context));
      case Blockquote blockquote -> List.of(renderBlockQuote(blockquote, context));
      case Rule _ -> List.of("---");
      case CodeBlock codeBlock -> List.of(renderCodeBlock(codeBlock));
      case Panel panel -> List.of(renderPanel(panel, context));
      case TaskList taskList -> List.of(listRenderer.renderTaskList(taskList, context, this));
      case TaskItem taskItem -> List.of(listRenderer.renderTaskItem(taskItem, context, this));
      case BlockTaskItem blockTaskItem ->
        List.of(listRenderer.renderBlockTaskItem(blockTaskItem, context, this));
      case BulletList bulletList -> List.of(listRenderer.renderBulletList(bulletList, context, this));
      case OrderedList orderedList ->
        List.of(listRenderer.renderOrderedList(orderedList, context, this));
      case DecisionList decisionList ->
        List.of(listRenderer.renderDecisionList(decisionList, context, this));
      case DecisionItem decisionItem ->
        List.of(listRenderer.renderDecisionItem(decisionItem, context, this));
      case ListItem listItem -> renderBlocks(listItem.content(), context);
      case Table table -> List.of(tableRenderer.renderTable(table, context, this));
      case TableRow row -> List.of(tableRenderer.renderTableRow(row, context, this));
      case TableCell cell -> List.of(tableRenderer.renderTableCell(cell, context, this));
      case Expand expand -> List.of(renderExpand(expand.title(), expand.content(), context));
      case NestedExpand expand -> List.of(renderExpand(expand.title(), expand.content(), context));
      case LayoutSection layout -> List.of(renderLayoutSection(layout, context));
      case LayoutColumn column -> renderBlocks(column.content(), context);
      case MediaSingle mediaSingle -> List.of(mediaRenderer.renderMediaSingle(mediaSingle, context, this));
      case MediaGroup mediaGroup -> List.of(mediaRenderer.renderMediaGroup(mediaGroup, context, this));
      case Media media -> List.of(mediaRenderer.renderMedia(media, context, this));
      case Caption caption -> List.of(mediaRenderer.renderCaption(caption, context, this));
      case Extension extension -> List.of(macroRenderer.renderExtension(extension, context));
      case BodiedExtension bodied -> macroRenderer.renderBodiedExtension(bodied, context, this);
      case MultiBodiedExtension mbe -> macroRenderer.renderMultiBodiedExtension(mbe, context, this);
      case ExtensionFrame frame -> renderBlocks(frame.content(), context);
      case SyncBlock sync -> List.of(macroRenderer.renderSyncBlock(sync));
      case BodiedSyncBlock sync -> macroRenderer.renderBodiedSyncBlock(sync, context, this);
      case BlockCard blockCard -> List.of(cardRenderer.renderBlockCard(blockCard.attrs()));
      case EmbedCard embedCard -> List.of(cardRenderer.renderEmbedCard(embedCard.attrs()));
      case UnknownBlock unknown -> renderUnknownBlockByPolicy(unknown.type(), context);
    };
  }

  @Override
  public List<String> renderBlocks(List<AdfBlock> blocks, RendererState context) {
    if (blocks == null || blocks.isEmpty()) {
      return List.of();
    }
    return blocks.stream()
        .<String>mapMulti((child, downstream) -> renderBlock(child, context).forEach(downstream))
        .toList();
  }

  // startAtLineStart enables leading-block escaping for inlines at output column 0 (paragraphs, a
  // list item's first paragraph, a caption); false for mid-line text (headings, task/decision items).
  @Override
  public String renderInlineNodes(
      List<AdfInline> nodes, RendererState context, boolean startAtLineStart) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }
    var builder = new StringBuilder();
    var atLineStart = startAtLineStart;
    for (var node : coalesceAdjacentText(nodes)) {
      var rendered = renderInline(node, context, atLineStart);
      builder.append(rendered);
      if (node instanceof HardBreak) {
        // A hard break starts a fresh output line, so the next node sits at line start again.
        atLineStart = true;
      } else if (!rendered.isEmpty()) {
        atLineStart = false;
      }
    }
    return builder.toString();
  }

  /**
   * Merges consecutive {@link Text} inlines with the same mark set into one node, so an adjacent
   * same-mark run gets a single set of delimiters instead of one per node. Non-Text inlines break a run.
   */
  private static List<AdfInline> coalesceAdjacentText(List<AdfInline> nodes) {
    var result = new ArrayList<AdfInline>(nodes.size());
    var run = new StringBuilder();
    List<AdfMark> runMarks = null;
    for (var node : nodes) {
      if (node instanceof Text text) {
        if (runMarks != null && sameMarkSet(runMarks, text.marks())) {
          run.append(text.text());
        } else {
          if (runMarks != null) {
            result.add(new Text(run.toString(), runMarks));
            run.setLength(0);
          }
          run.append(text.text());
          runMarks = text.marks();
        }
      } else {
        if (runMarks != null) {
          result.add(new Text(run.toString(), runMarks));
          run.setLength(0);
          runMarks = null;
        }
        result.add(node);
      }
    }
    if (runMarks != null) {
      result.add(new Text(run.toString(), runMarks));
    }
    return result;
  }

  // Order-insensitive value equality.
  private static boolean sameMarkSet(List<AdfMark> left, List<AdfMark> right) {
    return left.size() == right.size() && left.containsAll(right) && right.containsAll(left);
  }

  @Override
  public String applyMarks(String text, List<AdfMark> marks, boolean htmlVisualMarks) {
    return markRenderer.applyMarks(text, marks, htmlVisualMarks);
  }

  private String joinBlocks(List<String> blocks) {
    return RenderBuffer.joinBlocks(blocks);
  }

  private String renderInline(AdfInline node, RendererState context, boolean atLineStart) {
    return switch (node) {
      case Text text -> renderText(text, context, atLineStart);
      case HardBreak _ -> hardBreakMarker(context);
      case InlineCard card -> cardRenderer.renderInlineCard(card.attrs());
      case MediaInline media -> mediaRenderer.renderMediaInline(media, context, this);
      // Attribute-derived text is escaped like literal text, honouring atLineStart.
      case Date date ->
        MarkdownText.escapeInlineText(AdfText.dateFromTimestamp(date.timestamp()), atLineStart);
      case Emoji emoji -> MarkdownText.escapeInlineText(renderEmoji(emoji), atLineStart);
      case Mention mention -> MarkdownText.escapeInlineText(renderMention(mention), atLineStart);
      case Placeholder placeholder ->
        MarkdownText.escapeInlineText(placeholder.text(), atLineStart);
      case Status status -> MarkdownText.labelToken(statusLabel(status));
      case InlineExtension extension ->
        macroRenderer.renderInlineExtension(extension, context);
      case UnknownInline unknown -> renderUnknownInlineByPolicy(unknown.type(), context);
    };
  }

  private String renderParagraph(Paragraph paragraph, RendererState context) {
    // Paragraphs start at column 0, so the first inline is at line start (the promotion-prone case).
    var rendered = renderInlineNodes(paragraph.content(), context, true);
    if (rendered.isBlank()) {
      return rendered;
    }
    // Prefix after escaping, so the nbsp indent run isn't itself marker-neutralised.
    var prefixed = indentationPrefix(paragraph.marks()) + rendered;
    var styled = applyMarks(prefixed, paragraph.marks(), context.htmlVisualMarks());
    return alignmentWrap(styled, paragraph.marks(), context.htmlVisualMarks());
  }

  private String renderHeading(Heading heading, RendererState context) {
    var level = AdfText.clampHeadingLevel(heading.level());
    var text = renderHeadingText(heading.content(), context);
    if (text.isBlank()) {
      var blankAnchor = HeadingContent.extractAnchorId(heading.content());
      return blankAnchor == null || blankAnchor.isBlank() ? "" : HtmlFragments.anchor(blankAnchor);
    }

    var headingInfo = context.headingInfo(heading);
    var anchor = headingInfo == null ? null : headingInfo.anchor();
    var markup = "%s %s%s".formatted("#".repeat(level), indentationPrefix(heading.marks()), text).trim();
    // Inject the stored-slug <a id> for explicit anchors and toc-linked headings, so toc links don't
    // depend on the consumer's slugger matching commonmark's.
    if (anchor != null && !anchor.isBlank()
        && (HeadingContent.hasExplicitAnchor(heading.content())
            || context.isTocReferenced(heading))) {
      markup = HtmlFragments.anchor(anchor) + "\n" + markup;
    }
    return alignmentWrap(markup, heading.marks(), context.htmlVisualMarks());
  }

  // Wraps a block in <div align> for a center/end Alignment mark when htmlVisualMarks is on.
  private static String alignmentWrap(String body, List<AdfMark> marks, boolean htmlVisualMarks) {
    if (!htmlVisualMarks || body.isBlank()) {
      return body;
    }
    for (var mark : marks) {
      if (mark instanceof Alignment alignment) {
        var align = switch (alignment.align() == null ? "" : alignment.align()) {
          case "center" -> "center";
          case "end" -> "right";
          default -> null;
        };
        if (align != null) {
          // Blank lines around the body so CommonMark parses it as markdown, not a raw-HTML block.
          return "<div align=\"" + align + "\">\n\n" + body + "\n\n</div>";
        }
      }
    }
    return body;
  }

  private String renderHeadingText(List<AdfInline> content, RendererState context) {
    var headingNodes = HeadingContent.normalizedHeadingNodes(content);
    if (headingNodes.isEmpty()) {
      return "";
    }
    return renderHeadingInlines(headingNodes, context).strip();
  }

  /**
   * Renders heading inlines, inserting one space between an inline image and adjacent text so they
   * don't glue (e.g. {@code ![icon](src) Title}). All-text headings are unaffected.
   */
  private String renderHeadingInlines(List<AdfInline> nodes, RendererState context) {
    var headingContext = context.withHeading(true);
    var builder = new StringBuilder();
    AdfInline previous = null;
    for (var node : coalesceAdjacentText(nodes)) {
      var rendered = renderInline(node, headingContext, false);
      if (rendered.isEmpty()) {
        continue;
      }
      if (needsImageSeparator(previous, node, builder, rendered)) {
        builder.append(' ');
      }
      builder.append(rendered);
      previous = node;
    }
    return builder.toString();
  }

  private static boolean needsImageSeparator(
      AdfInline previous, AdfInline current, StringBuilder builder, String rendered) {
    if (!(previous instanceof MediaInline) && !(current instanceof MediaInline)) {
      return false;
    }
    if (builder.isEmpty()) {
      return false;
    }
    return !Character.isWhitespace(builder.charAt(builder.length() - 1))
        && !Character.isWhitespace(rendered.charAt(0));
  }

  // U+00A0 per indent step; ASCII spaces are unreliable as leading indent (4+ become a code block).
  private static final String INDENT_UNIT = "    ";

  // A run of non-breaking spaces for the block's Indentation mark (level 1..6), or "" when absent.
  private static String indentationPrefix(List<AdfMark> marks) {
    for (var mark : marks) {
      if (mark instanceof Indentation indentation) {
        return INDENT_UNIT.repeat(Math.clamp(indentation.level(), 0, 6));
      }
    }
    return "";
  }

  private String renderBlockQuote(Blockquote blockquote, RendererState context) {
    var content = joinBlocks(renderBlocks(blockquote.content(), context));
    return toBlockQuote(content);
  }

  private String renderPanel(Panel panel, RendererState context) {
    var header = "> [!" + gfmAlert(panel.panelType()) + "]";
    var content = joinBlocks(renderBlocks(panel.content(), context));
    if (content.isBlank()) {
      return header;
    }
    return header + "\n" + toBlockQuote(content);
  }

  /**
   * Maps an Atlassian panel type to a GFM alert keyword: warning -&gt; WARNING, error -&gt; CAUTION,
   * tip/success -&gt; TIP, everything else (info/note/custom/unknown) -&gt; NOTE.
   */
  private static String gfmAlert(String panelType) {
    if (panelType == null) {
      return "NOTE";
    }
    return switch (panelType.toLowerCase(Locale.ROOT)) {
      case "warning" -> "WARNING";
      case "error" -> "CAUTION";
      case "tip", "success" -> "TIP";
      default -> "NOTE";
    };
  }

  private String toBlockQuote(String value) {
    if (value.isBlank()) {
      return "";
    }
    var lines = MarkdownText.splitLines(value).stream().map(line -> line.isBlank() ? ">" : "> " + line).toList();
    return String.join("\n", lines);
  }

  private String renderCodeBlock(CodeBlock codeBlock) {
    var language = codeBlock.language();
    var body = codeBlock.text();
    // Fence must exceed the longest backtick run in the body, else an embedded ``` would close it.
    var ticks = "`".repeat(Math.max(3, MarkdownText.longestBacktickRun(body) + 1));
    var openingFence = (ticks + (language == null ? "" : language)).stripTrailing();
    return "%s\n%s\n%s".formatted(openingFence, body, ticks).stripTrailing();
  }

  private String renderLayoutSection(LayoutSection layout, RendererState context) {
    var columns = layout.content();
    if (columns.isEmpty()) {
      return "";
    }

    return joinBlocks(columns.stream()
        .flatMap(column -> renderBlocks(column.content(), context).stream())
        .toList());
  }

  private String renderExpand(String title, List<AdfBlock> content, RendererState context) {
    var safeTitle = title == null ? "" : title.trim();
    var body = joinBlocks(renderBlocks(content, context));
    var summary = safeTitle.isBlank()
        ? "<summary></summary>"
        : "<summary>" + HtmlFragments.escapeHtmlText(safeTitle) + "</summary>";
    if (body.isBlank()) {
      return "<details>" + summary + "</details>";
    }
    return "<details>" + summary + "\n\n" + body + "\n\n</details>";
  }

  private String renderText(Text text, RendererState context, boolean atLineStart) {
    // Code-marked text is literal (applyMarks wraps it in backticks), so it must not be escaped.
    var hasCodeMark = text.marks().stream().anyMatch(Code.class::isInstance);
    if (hasCodeMark) {
      return applyMarks(text.text(), text.marks(), context.htmlVisualMarks());
    }
    // Suppress leading-block escaping only in a GFM pipe cell (inline context). An HTML-fragment cell
    // is re-parsed as a block document, so a leading marker there must still be neutralized.
    var atLineStartEscaping = atLineStart && context.tableCell() != TableCellKind.GFM;
    var escaped = MarkdownText.escapeInlineText(text.text(), atLineStartEscaping);
    return applyMarks(escaped, text.marks(), context.htmlVisualMarks());
  }

  private String hardBreakMarker(RendererState context) {
    // An interior break would terminate an ATX heading, so collapse it to a space.
    if (context.inHeading()) {
      return " ";
    }
    // In any table cell a break is a single "\n" (one <br> after unwrap), not the two-space form.
    return context.tableCell() != TableCellKind.NONE ? "\n" : "  \n";
  }

  private String renderEmoji(Emoji emoji) {
    var text = Stream.of(emoji.text(), emoji.shortName())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    return text != null ? text : "";
  }

  private String renderMention(Mention mention) {
    var text = mention.text();
    if (text != null && !text.isBlank()) {
      return text;
    }
    // Fall back to the account id (or localId) so the mention isn't reduced to an opaque marker.
    var id = Stream.of(mention.id(), mention.localId())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    return id != null ? "@" + id : "@unknown";
  }

  private String statusLabel(Status status) {
    var text = status.text();
    return text == null || text.isBlank() ? "status" : text;
  }

  private List<String> renderUnknownBlockByPolicy(String nodeType, RendererState context) {
    var label = (nodeType == null || nodeType.isBlank()) ? "<empty>" : nodeType;
    return switch (context.unknownNodePolicy()) {
      case SKIP -> {
        log.warn("Skipping unsupported ADF block node type: {}", label);
        yield List.of();
      }
      case FAIL -> throw new IllegalStateException("Unsupported ADF block node type: " + label);
      case PLACEHOLDER -> {
        log.warn("Rendering placeholder for unsupported ADF block node type: {}", label);
        yield nodeType == null || nodeType.isBlank()
            ? List.of()
            : List.of(MarkdownText.labelToken("Unsupported: " + nodeType));
      }
    };
  }

  private String renderUnknownInlineByPolicy(String nodeType, RendererState context) {
    var label = (nodeType == null || nodeType.isBlank()) ? "<empty>" : nodeType;
    return switch (context.unknownNodePolicy()) {
      case SKIP -> {
        log.warn("Skipping unsupported ADF inline node type: {}", label);
        yield "";
      }
      case FAIL -> throw new IllegalStateException("Unsupported ADF inline node type: " + label);
      case PLACEHOLDER -> {
        log.warn("Rendering placeholder for unsupported ADF inline node type: {}", label);
        yield nodeType == null || nodeType.isBlank()
            ? ""
            : MarkdownText.labelToken("Unsupported inline: " + nodeType);
      }
    };
  }
}
