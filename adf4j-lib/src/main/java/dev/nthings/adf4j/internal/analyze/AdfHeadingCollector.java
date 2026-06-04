package dev.nthings.adf4j.internal.analyze;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import dev.nthings.adf4j.metadata.HeadingReference;
import dev.nthings.adf4j.internal.AdfText;
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
import dev.nthings.adf4j.ast.Extension;
import dev.nthings.adf4j.ast.ExtensionFrame;
import dev.nthings.adf4j.ast.HardBreak;
import dev.nthings.adf4j.ast.Heading;
import dev.nthings.adf4j.ast.InlineCard;
import dev.nthings.adf4j.ast.InlineExtension;
import dev.nthings.adf4j.ast.LayoutColumn;
import dev.nthings.adf4j.ast.LayoutSection;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.MacroParams;
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

final class AdfHeadingCollector {

  HeadingOutline collect(AdfDocument document) {
    if (document == null) {
      return HeadingOutline.empty();
    }

    var walk = new Walk();
    walk.blocks(document.content());

    var headings = new ArrayList<HeadingReference>();
    var idGenerator = IdGenerator.builder().defaultId("section").build();
    var headingsByNode = new IdentityHashMap<Heading, HeadingReference>();

    for (var heading : walk.headings) {
      var level = AdfText.clampHeadingLevel(heading.level());
      var headingText = extractHeadingPlainText(heading.content());
      if (headingText.isBlank()) {
        continue;
      }

      var anchor = HeadingContent.extractAnchorId(heading.content());
      if (anchor == null || anchor.isBlank()) {
        anchor = idGenerator.generateId(headingText);
      }

      var headingRef = new HeadingReference(level, headingText, anchor);
      headings.add(headingRef);
      headingsByNode.put(heading, headingRef);
    }

    return HeadingOutline.of(headings, headingsByNode, walk.tocReferencedLevels);
  }

  private static String extractHeadingPlainText(List<AdfInline> content) {
    var inlineNodes = HeadingContent.normalizedHeadingNodes(content);
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
        case Date date -> appendPlainText(builder, AdfText.dateFromTimestamp(date.timestamp()));
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

  // A single pass that records every heading and the union of all toc macros' level ranges, so the
  // injected-anchor set equals the set the rendered toc actually links.
  private static final class Walk {

    private final List<Heading> headings = new ArrayList<>();
    private final Set<Integer> tocReferencedLevels = new HashSet<>();

    private void blocks(List<AdfBlock> blocks) {
      for (var block : blocks) {
        block(block);
      }
    }

    private void block(AdfBlock block) {
      switch (block) {
        case Heading heading -> headings.add(heading);
        case Paragraph p -> inlines(p.content());
        case TaskItem item -> inlines(item.content());
        case DecisionItem item -> inlines(item.content());
        case Caption caption -> inlines(caption.content());
        case ListItem item -> blocks(item.content());
        case Extension extension -> recordToc(extension.extensionType(), extension.extensionKey(),
            extension.macroParams());
        case Blockquote bq -> blocks(bq.content());
        case Panel p -> blocks(p.content());
        case BulletList bl -> bl.content().forEach(item -> blocks(item.content()));
        case OrderedList ol -> ol.content().forEach(item -> blocks(item.content()));
        case TaskList tl -> blocks(tl.content());
        case BlockTaskItem bti -> blocks(bti.content());
        case DecisionList dl -> dl.content().forEach(this::block);
        case Table table -> table.content().forEach(this::block);
        case TableRow row -> row.content().forEach(this::block);
        case TableCell cell -> blocks(cell.content());
        case Expand expand -> blocks(expand.content());
        case NestedExpand expand -> blocks(expand.content());
        case LayoutSection layout -> layout.content().forEach(this::block);
        case LayoutColumn column -> blocks(column.content());
        case BodiedExtension extension -> blocks(extension.content());
        case MultiBodiedExtension mbe -> blocks(mbe.content());
        case ExtensionFrame frame -> blocks(frame.content());
        case BodiedSyncBlock sync -> blocks(sync.content());
        default -> {
        }
      }
    }

    private void inlines(List<AdfInline> inlines) {
      for (var inline : inlines) {
        if (inline instanceof InlineExtension extension) {
          recordToc(
              extension.extensionType(), extension.extensionKey(), extension.macroParams());
        }
      }
    }

    private void recordToc(String extensionType, String extensionKey, MacroParams macroParams) {
      if (!ConfluenceSupport.isConfluenceMacroExtension(extensionType)
          || !"toc".equals(extensionKey)) {
        return;
      }
      var range = TocLevelRange.of(macroParams);
      for (var level = range.min(); level <= range.max(); level++) {
        tocReferencedLevels.add(level);
      }
    }
  }
}
