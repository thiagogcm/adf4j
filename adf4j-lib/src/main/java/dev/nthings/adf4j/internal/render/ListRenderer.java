package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.AdfInline;
import dev.nthings.adf4j.ast.AdfNode;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;
import dev.nthings.adf4j.ast.TaskListContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

final class ListRenderer {

  private final BlockRenderer blockRenderer;
  private final InlineRenderer inlineRenderer;

  ListRenderer(BlockRenderer blockRenderer, InlineRenderer inlineRenderer) {
    this.blockRenderer = blockRenderer;
    this.inlineRenderer = inlineRenderer;
  }

  private enum ItemStyle {
    // First paragraph at block start, so leading markers are escaped. Sublists stay tight.
    LIST_ITEM(true, false, true),
    // Checkbox precedes the text (mid-line). An ordered list opening the item needs a blank line
    // to keep a start other than 1.
    TASK_ITEM(false, true, false);

    private final boolean paragraphAtLineStart;
    private final boolean blankBeforeRenumberedList;
    private final boolean tightNesting;

    ItemStyle(
        boolean paragraphAtLineStart, boolean blankBeforeRenumberedList, boolean tightNesting) {
      this.paragraphAtLineStart = paragraphAtLineStart;
      this.blankBeforeRenumberedList = blankBeforeRenumberedList;
      this.tightNesting = tightNesting;
    }
  }

  String renderTaskList(TaskList node, RenderContext context) {
    var lines = new ArrayList<String>();
    var previousWasNested = false;
    for (var item : context.children(node, TaskListContent.class)) {
      var itemContext = context.at(item);
      switch (item.node()) {
        case TaskItem taskItem -> {
          lines.add(renderTaskItem(taskItem, itemContext));
          previousWasNested = false;
        }
        case BlockTaskItem blockTaskItem -> {
          lines.addAll(blockTaskItemLines(blockTaskItem, itemContext));
          previousWasNested = false;
        }
        case TaskList nested -> {
          var rendered =
              MarkdownBlocks.indentLines(
                  renderTaskList(nested, itemContext), MarkdownBlocks.LIST_INDENT);
          if (!rendered.isEmpty()) {
            if (previousWasNested) {
              lines.addAll(separatorLines(MarkdownBlocks.LIST_INDENT));
            }
            lines.addAll(rendered);
            previousWasNested = true;
          }
        }
      }
    }

    return String.join("\n", lines);
  }

  String renderTaskItem(TaskItem node, RenderContext context) {
    var content =
        inlineRenderer.renderInlineNodes(context.children(node, AdfInline.class), context, false);
    return String.join(
        "\n", prefixParagraph(checklistPrefix(node.state()), content, MarkdownBlocks.LIST_INDENT));
  }

  String renderBlockTaskItem(BlockTaskItem node, RenderContext context) {
    return String.join("\n", blockTaskItemLines(node, context));
  }

  private List<String> blockTaskItemLines(BlockTaskItem node, RenderContext context) {
    return renderItem(
        checklistPrefix(node.state()),
        MarkdownBlocks.LIST_INDENT,
        node,
        ItemStyle.TASK_ITEM,
        context);
  }

  String renderBulletList(BulletList node, RenderContext context) {
    return renderListItems(node, context, _ -> "-");
  }

  String renderOrderedList(OrderedList node, RenderContext context) {
    var start = node.order();
    return renderListItems(node, context, index -> (start + index) + ".");
  }

  private String renderListItems(AdfNode list, RenderContext context, IntFunction<String> marker) {
    var items = context.children(list, ListItem.class);
    var lines = new ArrayList<String>();
    for (var index = 0; index < items.size(); index++) {
      var itemMarker = marker.apply(index);
      // Subtrees render locally; the marker width is added once, here.
      var indent = " ".repeat(itemMarker.length() + 1);
      lines.addAll(
          renderItem(
              itemMarker + " ",
              indent,
              items.get(index).node(),
              ItemStyle.LIST_ITEM,
              context.at(items.get(index))));
    }
    return String.join("\n", lines);
  }

  // `prefix` opens the first line, `indent` aligns the rest. `context` sits at `item`.
  private List<String> renderItem(
      String prefix, String indent, AdfNode item, ItemStyle style, RenderContext context) {
    var blocks = context.children(item, AdfBlock.class);
    if (blocks.isEmpty()) {
      return List.of(prefix.stripTrailing());
    }

    var located = blocks.getFirst();
    var first = located.node();
    var lines = new ArrayList<String>();
    String previous;
    if (first instanceof Paragraph paragraph) {
      previous =
          blockRenderer
              .renderParagraph(paragraph, context.at(located), style.paragraphAtLineStart)
              .stripTrailing();
      lines.addAll(prefixParagraph(prefix, previous, indent));
    } else {
      previous = blockRenderer.renderBlock(first, context.at(located));
      lines.add(prefix.stripTrailing());
      if (style.blankBeforeRenumberedList
          && first instanceof OrderedList orderedList
          && orderedList.order() != 1) {
        lines.add("");
      }
      lines.addAll(MarkdownBlocks.indentLines(previous, indent));
    }

    for (var next : blocks.subList(1, blocks.size())) {
      var block = next.node();
      var rendered = blockRenderer.renderBlock(block, context.at(next));
      if (rendered.isEmpty()) {
        continue;
      }
      // Continuation blocks need a blank line, or they soft-wrap into the previous paragraph. After
      // a bare marker a blank line would end the item, so the first block takes none, unless a
      // checkbox precedes it (then it is a paragraph).
      if (MarkdownBlocks.needsListSeparator(previous, rendered)) {
        lines.addAll(separatorLines(indent));
      } else if (!style.tightNesting || (!previous.isEmpty() && !isNestedListBlock(block))) {
        lines.add("");
      }
      lines.addAll(MarkdownBlocks.indentLines(rendered, indent));
      previous = rendered;
    }

    return lines;
  }

  // Lists nest tight and marker-aligned; other blocks get a blank line. An ordered list not
  // starting at 1 is the exception: CommonMark interrupts a paragraph only from 1, so it needs
  // the blank line or it (and its number) is lost.
  private static boolean isNestedListBlock(AdfBlock block) {
    return block instanceof BulletList
        || (block instanceof OrderedList orderedList && orderedList.order() == 1)
        || block instanceof TaskList
        || block instanceof DecisionList;
  }

  private static List<String> separatorLines(String indent) {
    return List.of("", indent + MarkdownBlocks.LIST_SEPARATOR, "");
  }

  String renderDecisionList(DecisionList node, RenderContext context) {
    var lines = new ArrayList<String>();
    for (var item : context.children(node, DecisionItem.class)) {
      lines.add(renderDecisionItem(item.node(), context.at(item)));
    }
    return String.join("\n", lines);
  }

  String renderDecisionItem(DecisionItem node, RenderContext context) {
    var state = node.state();
    var label =
        context
            .session()
            .writer()
            .label(state == null || state.isBlank() ? "decision" : "decision:" + state);
    var content =
        inlineRenderer.renderInlineNodes(context.children(node, AdfInline.class), context, false);
    return String.join(
        "\n", prefixParagraph("- " + label + " ", content, MarkdownBlocks.LIST_INDENT));
  }

  private String checklistPrefix(@Nullable String state) {
    return "- [" + ("DONE".equalsIgnoreCase(state) ? "x" : " ") + "] ";
  }

  // Wrapped lines align to the item's content column.
  private List<String> prefixParagraph(String prefix, String text, String continuationIndent) {
    if (text.isBlank()) {
      return List.of(prefix.stripTrailing());
    }

    var parts = MarkdownText.splitLines(text);
    var lines = new ArrayList<String>(parts.size());
    lines.add(prefix + parts.getFirst());
    for (var index = 1; index < parts.size(); index++) {
      lines.add(MarkdownBlocks.indentLine(parts.get(index), continuationIndent));
    }
    return lines;
  }
}
