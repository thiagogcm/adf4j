package dev.nthings.adf4j.renderer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import dev.nthings.adf4j.RenderOptions;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.internal.MarkdownText;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfMark;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.CardAttrs;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdfRenderer {

  private static final Logger log = LoggerFactory.getLogger(AdfRenderer.class);

  private static final Map<String, String> STATUS_BACKGROUND = Map.of(
      "neutral", "#DFE1E6",
      "purple", "#998DD9",
      "blue", "#0052CC",
      "red", "#DE350B",
      "yellow", "#FFAB00",
      "green", "#00875A");
  private static final Map<String, String> STATUS_FOREGROUND = Map.of(
      "neutral", "#172B4D",
      "yellow", "#172B4D");
  private static final String STATUS_FOREGROUND_DEFAULT = "#FFFFFF";

  private final AdfHeadingCollector headingCollector;
  private final TextMarkRenderer markRenderer;
  private final ListRenderer listRenderer;
  private final TableRenderer tableRenderer;
  private final MediaRenderer mediaRenderer;
  private final MacroRenderer macroRenderer;

  public AdfRenderer(
      AdfHeadingCollector headingCollector,
      TextMarkRenderer markRenderer,
      ListRenderer listRenderer,
      TableRenderer tableRenderer,
      MediaRenderer mediaRenderer,
      MacroRenderer macroRenderer) {
    this.headingCollector = headingCollector;
    this.markRenderer = markRenderer;
    this.listRenderer = listRenderer;
    this.tableRenderer = tableRenderer;
    this.mediaRenderer = mediaRenderer;
    this.macroRenderer = macroRenderer;
  }

  public String render(
      AdfDocument document,
      RenderOptions options,
      RenderingStrategy strategy,
      HeadingOutline headingOutline) {
    if (document == null) {
      return "";
    }

    var requiredOptions = Objects.requireNonNull(options, "options");
    var renderingStrategy = Objects.requireNonNullElseGet(strategy, RenderingStrategies::storage);
    var outline = headingOutline == null ? headingCollector.collect(document) : headingOutline;
    var context = RenderContext.root(requiredOptions, outline, renderingStrategy);
    return joinBlocks(renderBlocks(document.content(), context));
  }

  public List<String> renderBlock(AdfBlock block, RenderContext context) {
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
      case Extension extension -> List.of(macroRenderer.renderExtension(extension, context, this));
      case BodiedExtension bodied -> macroRenderer.renderBodiedExtension(bodied, context, this);
      case SyncBlock sync -> List.of(macroRenderer.renderSyncBlock(sync));
      case BodiedSyncBlock sync -> macroRenderer.renderBodiedSyncBlock(sync, context, this);
      case BlockCard blockCard -> List.of(renderBlockCard(blockCard.attrs(), context));
      case EmbedCard embedCard -> List.of(renderEmbedCard(embedCard.attrs(), context));
      case UnknownBlock unknown -> renderUnknownBlockByPolicy(unknown.type(), context);
    };
  }

  public List<String> renderBlocks(List<AdfBlock> blocks, RenderContext context) {
    if (blocks == null || blocks.isEmpty()) {
      return List.of();
    }
    return blocks.stream()
        .<String>mapMulti((child, downstream) -> renderBlock(child, context).forEach(downstream))
        .toList();
  }

  public String renderInlineNodes(List<AdfInline> nodes, RenderContext context) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }
    var builder = new StringBuilder();
    for (var node : nodes) {
      builder.append(renderInline(node, context));
    }
    return builder.toString();
  }

  public String applyMarks(String text, List<AdfMark> marks, RenderContext context) {
    return markRenderer.applyMarks(
        text,
        marks,
        context.strategy(),
        (href, renderedLabel) -> resolveLink(href, renderedLabel, context));
  }

  public String joinBlocks(List<String> blocks) {
    return RenderBuffer.joinBlocks(blocks);
  }

  private String renderInline(AdfInline node, RenderContext context) {
    return switch (node) {
      case Text text -> renderText(text, context);
      case HardBreak _ -> hardBreakMarker(context);
      case InlineCard card -> renderInlineCard(card.attrs(), context);
      case MediaInline media -> mediaRenderer.renderMediaInline(context.strategy(), media, context, this);
      case Date date -> MarkdownText.dateFromTimestamp(date.timestamp());
      case Emoji emoji -> renderEmoji(emoji);
      case Mention mention -> renderMention(mention);
      case Placeholder placeholder -> placeholder.text();
      case Status status -> renderStatus(status, context);
      case InlineExtension extension ->
        macroRenderer.renderInlineExtension(extension, context, this);
      case UnknownInline unknown -> renderUnknownInlineByPolicy(unknown.type(), context);
    };
  }

  private String renderParagraph(Paragraph paragraph, RenderContext context) {
    var standaloneExcerpt = macroRenderer.extractStandaloneExcerptInclude(paragraph.content());
    if (standaloneExcerpt != null) {
      return macroRenderer.renderInlineExtension(standaloneExcerpt, context, this);
    }

    var rendered = renderInlineNodes(paragraph.content(), context);
    return context
        .strategy()
        .formatParagraph(rendered, markRenderer.extractBlockStyles(paragraph.marks()));
  }

  private String renderHeading(Heading heading, RenderContext context) {
    var level = MarkdownText.clampHeadingLevel(heading.level());
    var text = renderHeadingText(heading.content(), context);
    if (text.isBlank()) {
      return "";
    }

    var renderedText = context.strategy().isStorage() ? text : tableRenderer.renderHtmlFragment(text);
    var headingInfo = context.headingInfo(heading);

    return context
        .strategy()
        .formatHeading(
            level,
            renderedText,
            headingInfo == null ? null : headingInfo.anchor(),
            markRenderer.extractBlockStyles(heading.marks()));
  }

  private String renderHeadingText(List<AdfInline> content, RenderContext context) {
    var headingNodes = AdfHeadingCollector.normalizedHeadingNodes(content);
    if (headingNodes.isEmpty()) {
      return "";
    }
    return renderInlineNodes(headingNodes, context).strip();
  }

  private String renderBlockQuote(Blockquote blockquote, RenderContext context) {
    var content = joinBlocks(renderBlocks(blockquote.content(), context));
    return toBlockQuote(content);
  }

  private String renderPanel(Panel panel, RenderContext context) {
    var alertHeader = "> [!" + gfmAlertType(panel.panelType()) + "]";
    var content = joinBlocks(renderBlocks(panel.content(), context));
    if (content.isBlank()) {
      return alertHeader;
    }
    return alertHeader + "\n" + toBlockQuote(content);
  }

  private static String gfmAlertType(String panelType) {
    if (panelType == null) {
      return "NOTE";
    }
    return switch (panelType.toLowerCase(Locale.ROOT)) {
      case "info" -> "IMPORTANT";
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
    var fence = "```" + (language == null ? "" : language);
    return "%s\n%s\n```".formatted(fence.stripTrailing(), codeBlock.text()).stripTrailing();
  }

  private String renderLayoutSection(LayoutSection layout, RenderContext context) {
    var columns = layout.content();
    if (columns.isEmpty()) {
      return "";
    }

    if (context.strategy().isStorage()) {
      return joinBlocks(columns.stream()
          .flatMap(column -> renderBlocks(column.content(), context).stream())
          .toList());
    }

    var cells = new StringBuilder("<table><tr>");
    for (var column : columns) {
      cells.append("<td");
      if (column.width() > 0) {
        cells.append(" style=\"width:").append(column.width()).append("%\"");
      }
      cells.append('>');
      cells.append(joinBlocks(renderBlocks(column.content(), context)));
      cells.append("</td>");
    }
    cells.append("</tr></table>");
    return cells.toString();
  }

  private String renderExpand(String title, List<AdfBlock> content, RenderContext context) {
    var safeTitle = title == null ? "" : title.trim();
    var body = joinBlocks(renderBlocks(content, context));
    var summary = safeTitle.isBlank() ? "<summary></summary>" : "<summary>" + safeTitle + "</summary>";
    if (body.isBlank()) {
      return "<details>" + summary + "</details>";
    }
    return "<details>" + summary + "\n\n" + body + "\n\n</details>";
  }

  private String renderText(Text text, RenderContext context) {
    return applyMarks(text.text(), text.marks(), context);
  }

  private String hardBreakMarker(RenderContext context) {
    return context.inTable() ? "\n" : "  \n";
  }

  private String resolveHref(String href, RenderContext context) {
    if (context.linkResolver() == null || context.currentPageId() == null) {
      return href;
    }
    var targetPageId = ConfluenceSupport.pageId(href);
    if (targetPageId != null) {
      return context.linkResolver().resolve(context.currentPageId(), targetPageId).orElse(href);
    }
    return href;
  }

  private ResolvedLink resolveLink(String href, String renderedLabel, RenderContext context) {
    var resolvedHref = resolveHref(href, context);
    var fallbackLabel = (renderedLabel == null || renderedLabel.isBlank()) ? resolvedHref : renderedLabel;
    if (!shouldUseResolvedPageTitle(fallbackLabel, href, resolvedHref)) {
      return new ResolvedLink(resolvedHref, fallbackLabel);
    }
    return new ResolvedLink(resolvedHref, resolveInternalPageTitle(href, fallbackLabel, context));
  }

  private boolean shouldUseResolvedPageTitle(
      String label, String originalHref, String resolvedHref) {
    if (label == null) {
      return true;
    }
    var normalizedLabel = label.strip();
    if (normalizedLabel.isEmpty()) {
      return true;
    }
    return Objects.equals(normalizedLabel, originalHref != null ? originalHref.strip() : "")
        || Objects.equals(normalizedLabel, resolvedHref != null ? resolvedHref.strip() : "");
  }

  private String renderBlockCard(CardAttrs attrs, RenderContext context) {
    var renderedUrl = renderCardUrl(attrs, context);
    if (renderedUrl != null) {
      return renderedUrl;
    }

    var identifier = Stream.of(attrs.datasourceId(), attrs.localId())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse(null);
    if (identifier == null || identifier.isBlank()) {
      return "[Card]";
    }
    return "[Card: %s]".formatted(identifier);
  }

  private String renderInlineCard(CardAttrs attrs, RenderContext context) {
    var url = renderCardUrl(attrs, context);
    return url != null ? url : "[Inline card]";
  }

  private String renderEmbedCard(CardAttrs attrs, RenderContext context) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return "[Embed card]";
    }

    var resolvedUrl = resolveHref(url, context);
    var explicitTitle = attrs.title();
    if (explicitTitle != null && !explicitTitle.isBlank()) {
      return "[%s](%s)".formatted(MarkdownText.escapeLinkText(explicitTitle), resolvedUrl);
    }
    return "<%s>".formatted(resolvedUrl);
  }

  private String renderCardUrl(CardAttrs attrs, RenderContext context) {
    var url = attrs.url();
    if (url == null || url.isBlank()) {
      return null;
    }

    var resolvedUrl = resolveHref(url, context);
    var label = resolveCardLabel(attrs, url, resolvedUrl, context);
    return "[%s](%s)".formatted(MarkdownText.escapeLinkText(label), resolvedUrl);
  }

  private String resolveCardLabel(
      CardAttrs attrs, String originalUrl, String resolvedUrl, RenderContext context) {
    var explicitTitle = attrs.title();
    if (explicitTitle != null && !explicitTitle.isBlank()) {
      return explicitTitle;
    }
    return resolveInternalPageTitle(originalUrl, resolvedUrl, context);
  }

  private String resolveInternalPageTitle(
      String href, String fallbackLabel, RenderContext context) {
    var targetPageId = ConfluenceSupport.pageId(href);
    if (targetPageId == null || context.pageTitleResolver() == null) {
      return fallbackLabel;
    }
    return context
        .pageTitleResolver()
        .resolve(targetPageId)
        .filter(s -> !s.isBlank())
        .orElse(fallbackLabel);
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
    if (text == null || text.isBlank()) {
      return "@mention";
    }
    return text;
  }

  private String renderStatus(Status status, RenderContext context) {
    var text = status.text();
    var safeText = text == null || text.isBlank() ? "status" : text;
    if (context.strategy().isStorage()) {
      return "[%s]".formatted(safeText);
    }
    var color = status.color();
    var normalized = color == null ? "neutral" : color.toLowerCase(Locale.ROOT);
    var background = STATUS_BACKGROUND.getOrDefault(normalized, STATUS_BACKGROUND.get("neutral"));
    var foreground = STATUS_FOREGROUND.getOrDefault(normalized, STATUS_FOREGROUND_DEFAULT);
    return "<span style=\"background-color:%s;color:%s\">%s</span>"
        .formatted(background, foreground, safeText);
  }

  private List<String> renderUnknownBlockByPolicy(String nodeType, RenderContext context) {
    var label = (nodeType == null || nodeType.isBlank()) ? "<empty>" : nodeType;
    return switch (context.unknownNodePolicy()) {
      case SKIP -> {
        log.warn("Skipping unsupported ADF block node type: {}", label);
        yield List.of();
      }
      case FAIL -> throw new IllegalStateException("Unsupported ADF block node type: " + label);
      case PLACEHOLDER -> {
        log.warn("Rendering placeholder for unsupported ADF block node type: {}", label);
        var placeholder = nodeType == null || nodeType.isBlank() ? "" : "[Unsupported: " + nodeType + "]";
        yield placeholder.isBlank() ? List.of() : List.of(placeholder);
      }
    };
  }

  private String renderUnknownInlineByPolicy(String nodeType, RenderContext context) {
    var label = (nodeType == null || nodeType.isBlank()) ? "<empty>" : nodeType;
    return switch (context.unknownNodePolicy()) {
      case SKIP -> {
        log.warn("Skipping unsupported ADF inline node type: {}", label);
        yield "";
      }
      case FAIL -> throw new IllegalStateException("Unsupported ADF inline node type: " + label);
      case PLACEHOLDER -> {
        log.warn("Rendering placeholder for unsupported ADF inline node type: {}", label);
        yield nodeType == null || nodeType.isBlank() ? "" : "[Unsupported inline: " + nodeType + "]";
      }
    };
  }
}
