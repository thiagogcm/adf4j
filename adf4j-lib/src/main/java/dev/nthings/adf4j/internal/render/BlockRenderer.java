package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.BlockCard;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.CodeBlock;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.EmbedCard;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.Media;
import dev.nthings.adf4j.ast.MediaGroup;
import dev.nthings.adf4j.ast.MediaSingle;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Rule;
import dev.nthings.adf4j.ast.SyncBlock;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.UnknownBlock;
import dev.nthings.adf4j.internal.AdfText;
import dev.nthings.adf4j.internal.Located;
import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.analyze.HeadingContent;
import dev.nthings.adf4j.result.ConversionEffect;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/// Renders blocks: dispatches each to its delegate and renders simple containers itself.
/// Builds its delegates in the constructor, handing them `this` to recurse into nested blocks.
final class BlockRenderer {

  private final TextMarkRenderer markRenderer;
  private final InlineRenderer inlineRenderer;
  private final ListRenderer listRenderer;
  private final TableRenderer tableRenderer;
  private final MediaRenderer mediaRenderer;
  private final MacroRenderer macroRenderer;
  private final CardRenderer cardRenderer;

  BlockRenderer(CellHtmlRenderer cellHtmlRenderer) {
    this.markRenderer = new TextMarkRenderer();
    this.cardRenderer = new CardRenderer();
    this.mediaRenderer = new MediaRenderer(markRenderer, this);
    this.macroRenderer = new MacroRenderer(mediaRenderer, this);
    this.inlineRenderer =
        new InlineRenderer(markRenderer, mediaRenderer, macroRenderer, cardRenderer);
    this.listRenderer = new ListRenderer(this, inlineRenderer);
    this.tableRenderer = new TableRenderer(this, cellHtmlRenderer);
  }

  // Right-trim so a custom renderer's trailing newline adds no blank lines to a parent.
  // `context` sits at the block, as everywhere below. A paragraph at column 0 starts a line.
  String renderBlock(AdfBlock block, RenderContext context) {
    return renderBlock(block, context, true);
  }

  /// Every block renders through here, so each is observed once. `atLineStart` is false only for
  /// a paragraph after a checkbox on the same line.
  String renderBlock(AdfBlock block, RenderContext context, boolean atLineStart) {
    context.session().events().observeMarks(block, context.path());
    return dispatchBlock(block, context, atLineStart).stripTrailing();
  }

  private String dispatchBlock(AdfBlock block, RenderContext context, boolean atLineStart) {
    return switch (block) {
      case Paragraph paragraph -> renderParagraph(paragraph, context, atLineStart);
      case Heading heading -> renderHeading(heading, context);
      case Blockquote blockquote -> renderBlockQuote(blockquote, context);
      case Rule _ -> "---";
      case CodeBlock codeBlock -> renderCodeBlock(codeBlock);
      case Panel panel -> renderPanel(panel, context);
      case TaskList taskList -> listRenderer.renderTaskList(taskList, context);
      case TaskItem taskItem -> listRenderer.renderTaskItem(taskItem, context);
      case BlockTaskItem blockTaskItem -> listRenderer.renderBlockTaskItem(blockTaskItem, context);
      case BulletList bulletList -> listRenderer.renderBulletList(bulletList, context);
      case OrderedList orderedList -> listRenderer.renderOrderedList(orderedList, context);
      case DecisionList decisionList -> listRenderer.renderDecisionList(decisionList, context);
      case DecisionItem decisionItem -> listRenderer.renderDecisionItem(decisionItem, context);
      case ListItem listItem -> renderBlocks(listItem, context);
      case Table table -> tableRenderer.renderTable(table, context);
      case TableRow row -> tableRenderer.renderTableRow(row, context);
      case TableCell cell -> tableRenderer.renderTableCell(cell, context);
      case Expand expand -> renderExpand(expand.title(), expand, context);
      case NestedExpand expand -> renderExpand(expand.title(), expand, context);
      case LayoutSection layout -> renderLayoutSection(layout, context);
      case LayoutColumn column -> renderBlocks(column, context);
      case MediaSingle mediaSingle -> mediaRenderer.renderMediaSingle(mediaSingle, context);
      case MediaGroup mediaGroup -> mediaRenderer.renderMediaGroup(mediaGroup, context);
      case Media media -> mediaRenderer.renderMedia(media, context);
      case Caption caption -> renderCaption(caption, context);
      case Extension extension -> macroRenderer.renderExtension(extension, context, true);
      case BodiedExtension bodied -> macroRenderer.renderBodiedExtension(bodied, context);
      case MultiBodiedExtension mbe -> macroRenderer.renderMultiBodiedExtension(mbe, context);
      case ExtensionFrame frame -> renderBlocks(frame, context);
      case SyncBlock sync -> macroRenderer.renderSyncBlock(sync, context);
      case BodiedSyncBlock sync -> macroRenderer.renderBodiedSyncBlock(sync, context);
      case BlockCard blockCard -> cardRenderer.renderBlockCard(blockCard, context);
      case EmbedCard embedCard -> cardRenderer.renderEmbedCard(embedCard, context);
      case UnknownBlock unknown -> inlineRenderer.renderUnknown(unknown, context);
    };
  }

  /// Block children of `parent` (which `context` sits at), joined.
  String renderBlocks(AdfNode parent, RenderContext context) {
    var rendered =
        context.children(parent, AdfBlock.class).stream()
            .map(block -> renderBlock(block.node(), context.at(block)))
            .toList();
    return context.site() == InlineSite.GFM_CELL
        ? MarkdownBlocks.joinWithoutLists(rendered)
        : MarkdownBlocks.join(rendered);
  }

  // A caption is its own block at column 0.
  String renderCaption(Caption node, RenderContext context) {
    return inlineRenderer.renderInlineNodes(context.children(node, AdfInline.class), context, true);
  }

  private String renderParagraph(Paragraph paragraph, RenderContext context, boolean atLineStart) {
    var rendered =
        inlineRenderer.renderInlineNodes(
            context.children(paragraph, AdfInline.class), context, atLineStart);
    if (rendered.isBlank()) {
      return rendered;
    }
    // Prefix after escaping, so the nbsp indent isn't neutralised.
    var prefixed = markRenderer.indentationPrefix(paragraph.marks()) + rendered;
    var styled = markRenderer.applyMarks(prefixed, paragraph.marks(), context);
    return markRenderer.alignmentWrap(
        styled, paragraph.marks(), context.session().options().htmlVisualMarks());
  }

  private String renderHeading(Heading heading, RenderContext context) {
    var text = renderHeadingText(heading, context);
    var anchor =
        HeadingAnchors.anchorToInject(
            context.session().headingOutline(), heading, context.path(), text.isBlank());
    if (text.isBlank()) {
      return HtmlFragments.anchor(anchor);
    }

    var level = AdfText.clampHeadingLevel(heading.level());
    var indent = markRenderer.indentationPrefix(heading.marks());
    var markup = "%s %s%s".formatted("#".repeat(level), indent, text).trim();
    if (anchor != null) {
      markup = HtmlFragments.anchor(anchor) + "\n" + markup;
    }
    return markRenderer.alignmentWrap(
        markup, heading.marks(), context.session().options().htmlVisualMarks());
  }

  private String renderHeadingText(Heading heading, RenderContext context) {
    var headingNodes =
        HeadingContent.normalized(context.children(heading, AdfInline.class), Located::node);
    var headingContext = context.withSite(InlineSite.HEADING);
    var rendered = inlineRenderer.renderInlineNodes(headingNodes, headingContext, false);
    // ATX heading is one line: collapse any breaks.
    return Strings.collapseLineBreaks(rendered).strip();
  }

  private String renderBlockQuote(Blockquote blockquote, RenderContext context) {
    return toBlockQuote(renderBlocks(blockquote, context));
  }

  private String renderPanel(Panel panel, RenderContext context) {
    var header = "> [!" + gfmAlert(panel.panelType()) + "]";
    var content = renderBlocks(panel, context);
    if (content.isBlank()) {
      return header;
    }
    return header + "\n" + toBlockQuote(content);
  }

  /// Panel type to GFM alert: warning -> WARNING, error -> CAUTION, tip/success -> TIP, else NOTE.
  private static String gfmAlert(@Nullable String panelType) {
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
    var lines =
        MarkdownText.splitLines(value).stream()
            .map(line -> line.isBlank() ? ">" : "> " + line)
            .toList();
    return String.join("\n", lines);
  }

  private String renderCodeBlock(CodeBlock codeBlock) {
    return MarkdownText.fencedCodeBlock(codeBlock.text(), codeBlock.language());
  }

  private String renderLayoutSection(LayoutSection layout, RenderContext context) {
    context
        .session()
        .events()
        .effect(
            ConversionEffect.Kind.STRUCTURE_CHANGED,
            context.path(),
            "Columns flattened into sequential blocks");
    return MarkdownBlocks.join(
        context.children(layout, LayoutColumn.class).stream()
            .map(column -> renderBlocks(column.node(), context.at(column)))
            .toList());
  }

  private String renderExpand(String title, AdfBlock expand, RenderContext context) {
    var safeTitle = title.trim();
    var body = renderBlocks(expand, context);
    var summary =
        safeTitle.isBlank()
            ? "<summary></summary>"
            : "<summary>" + HtmlFragments.escapeHtmlText(safeTitle) + "</summary>";
    if (body.isBlank()) {
      return "<details>" + summary + "</details>";
    }
    return "<details>" + summary + "\n\n" + body + "\n\n</details>";
  }
}
