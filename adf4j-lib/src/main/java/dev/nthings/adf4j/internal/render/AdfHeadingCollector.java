package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.internal.ConfluenceSupport;
import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfDocument;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.Blockquote;
import dev.nthings.adf4j.ast.BodiedExtension;
import dev.nthings.adf4j.ast.BodiedSyncBlock;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.Caption;
import dev.nthings.adf4j.ast.Date;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.Emoji;
import dev.nthings.adf4j.ast.Expand;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.MediaInline;
import dev.nthings.adf4j.ast.Mention;
import dev.nthings.adf4j.ast.MultiBodiedExtension;
import dev.nthings.adf4j.ast.NestedExpand;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Panel;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.Placeholder;
import dev.nthings.adf4j.ast.Status;
import dev.nthings.adf4j.ast.Table;
import dev.nthings.adf4j.ast.TableCell;
import dev.nthings.adf4j.ast.TableRow;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.Text;

import org.commonmark.ext.heading.anchor.IdGenerator;

public final class AdfHeadingCollector {

  public HeadingOutline collect(AdfDocument document) {
    if (document == null) {
      return HeadingOutline.empty();
    }

    var headings = new ArrayList<HeadingReference>();
    var idGenerator = IdGenerator.builder().defaultId("section").build();
    var headingsByNode = new IdentityHashMap<Heading, HeadingReference>();

    for (var heading : walkHeadings(document)) {
      var level = MarkdownText.clampHeadingLevel(heading.level());
      var headingText = extractHeadingPlainText(heading.content());
      if (headingText.isBlank()) {
        continue;
      }

      var anchor = extractAnchorId(heading.content());
      if (anchor == null || anchor.isBlank()) {
        anchor = idGenerator.generateId(headingText);
      }

      var headingRef = new HeadingReference(level, headingText, anchor);
      headings.add(headingRef);
      headingsByNode.put(heading, headingRef);
    }

    return HeadingOutline.of(headings, headingsByNode);
  }

  static List<AdfInline> normalizedHeadingNodes(List<AdfInline> content) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    var start = 0;
    while (start < content.size() && content.get(start) instanceof HardBreak) {
      start++;
    }
    var end = content.size();
    while (end > start && content.get(end - 1) instanceof HardBreak) {
      end--;
    }
    if (start >= end) {
      return List.of();
    }

    return content.subList(start, end).stream()
        .filter(node -> !isAnchorExtension(node))
        .toList();
  }

  private static String extractHeadingPlainText(List<AdfInline> content) {
    var inlineNodes = normalizedHeadingNodes(content);
    if (inlineNodes.isEmpty()) {
      return "";
    }

    var builder = new StringBuilder();
    for (var node : inlineNodes) {
      switch (node) {
        case Text text -> appendPlainText(builder, text.text());
        case HardBreak _ -> appendPlainText(builder, " ");
        case Emoji emoji ->
            appendPlainText(
                builder,
                Stream.of(emoji.text(), emoji.shortName())
                    .filter(s -> s != null && !s.isBlank())
                    .findFirst()
                    .orElse(""));
        case Mention mention -> appendPlainText(builder, mention.text());
        case Status status -> {
          if (status.text() != null && !status.text().isBlank()) {
            appendPlainText(builder, "[" + status.text() + "]");
          }
        }
        case InlineCard card -> {
          var title = card.attrs().title();
          appendPlainText(builder, title == null || title.isBlank() ? card.attrs().url() : title);
        }
        case MediaInline media -> {
          var alt = media.attrs().alt();
          appendPlainText(builder, alt == null || alt.isBlank() ? "media" : alt);
        }
        case Date date -> appendPlainText(builder, MarkdownText.dateFromTimestamp(date.timestamp()));
        case Placeholder placeholder -> appendPlainText(builder, placeholder.text());
        default -> {
        }
      }
    }
    return builder.toString().trim();
  }

  /**
   * Appends a heading-text fragment, inserting one space only when two sources would otherwise fuse
   * with no whitespace (so an image alt before {@code "Title"} slugs to {@code icon-title}, not
   * {@code icontitle}). Boundaries that already have whitespace are untouched.
   */
  private static void appendPlainText(StringBuilder builder, String fragment) {
    if (fragment == null || fragment.isEmpty()) {
      return;
    }
    if (!builder.isEmpty()
        && !Character.isWhitespace(builder.charAt(builder.length() - 1))
        && !Character.isWhitespace(fragment.charAt(0))) {
      builder.append(' ');
    }
    builder.append(fragment);
  }

  private static boolean isAnchorExtension(AdfInline node) {
    if (!(node instanceof InlineExtension extension)) {
      return false;
    }
    return ConfluenceSupport.isConfluenceMacroExtension(extension.extensionType())
        && "anchor".equals(extension.extensionKey());
  }

  /** True iff the heading carries an explicit Confluence {@code anchor} macro with a non-blank id. */
  static boolean hasExplicitAnchor(List<AdfInline> content) {
    var anchorId = extractAnchorId(content);
    return anchorId != null && !anchorId.isBlank();
  }

  static String extractAnchorId(List<AdfInline> content) {
    if (content == null || content.isEmpty()) {
      return null;
    }
    for (var node : content) {
      if (!(node instanceof InlineExtension extension)) {
        continue;
      }
      if (!isAnchorExtension(extension)) {
        continue;
      }
      var anchorId = ConfluenceSupport.anchorId(extension.macroParams());
      if (anchorId != null && !anchorId.isBlank()) {
        return anchorId;
      }
    }
    return null;
  }

  private List<Heading> walkHeadings(AdfDocument document) {
    var headings = new ArrayList<Heading>();
    walkBlocks(document.content(), headings);
    return headings;
  }

  private void walkBlocks(List<AdfBlock> blocks, List<Heading> headings) {
    for (var block : blocks) {
      walkBlock(block, headings);
    }
  }

  private void walkBlock(AdfBlock block, List<Heading> headings) {
    switch (block) {
      case Heading heading -> {
        headings.add(heading);
      }
      case Paragraph _, ListItem _, TaskItem _, DecisionItem _, Caption _ -> {
        // No nested headings inside inline-bearing leaves.
      }
      case Blockquote bq -> walkBlocks(bq.content(), headings);
      case Panel p -> walkBlocks(p.content(), headings);
      case BulletList bl -> bl.content().forEach(item -> walkBlocks(item.content(), headings));
      case OrderedList ol -> ol.content().forEach(item -> walkBlocks(item.content(), headings));
      case TaskList tl -> walkBlocks(tl.content(), headings);
      case BlockTaskItem bti -> walkBlocks(bti.content(), headings);
      case DecisionList dl -> {
        // Decision items only carry inlines.
        for (var item : dl.content()) {
          walkBlock(item, headings);
        }
      }
      case Table table -> table.content().forEach(row -> walkBlock(row, headings));
      case TableRow row -> row.content().forEach(cell -> walkBlock(cell, headings));
      case TableCell cell -> walkBlocks(cell.content(), headings);
      case Expand expand -> walkBlocks(expand.content(), headings);
      case NestedExpand expand -> walkBlocks(expand.content(), headings);
      case LayoutSection layout -> layout.content().forEach(column -> walkBlock(column, headings));
      case LayoutColumn column -> walkBlocks(column.content(), headings);
      case BodiedExtension extension -> walkBlocks(extension.content(), headings);
      case MultiBodiedExtension mbe -> walkBlocks(mbe.content(), headings);
      case ExtensionFrame frame -> walkBlocks(frame.content(), headings);
      case BodiedSyncBlock sync -> walkBlocks(sync.content(), headings);
      default -> {
      }
    }
  }
}
