package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.nthings.adf4j.ast.AdfBlock;
import dev.nthings.adf4j.ast.BlockTaskItem;
import dev.nthings.adf4j.ast.BulletList;
import dev.nthings.adf4j.ast.DecisionItem;
import dev.nthings.adf4j.ast.DecisionList;
import dev.nthings.adf4j.ast.ListItem;
import dev.nthings.adf4j.ast.OrderedList;
import dev.nthings.adf4j.ast.Paragraph;
import dev.nthings.adf4j.ast.TaskItem;
import dev.nthings.adf4j.ast.TaskList;

final class ListRenderer {

  String renderTaskList(TaskList node, RendererState context, BlockRecursion recursion) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof TaskItem taskItem) {
        lines.add(renderTaskItem(taskItem, context, recursion));
      } else if (item instanceof BlockTaskItem blockTaskItem) {
        lines.addAll(renderBlockTaskItemLines(blockTaskItem, context, recursion));
      } else if (item instanceof TaskList nested) {
        // Recurse one level deeper so checklistPrefix indents the nested checkboxes.
        var rendered =
            renderTaskList(nested, context.withListDepth(context.listDepth() + 1), recursion);
        if (!rendered.isBlank()) {
          lines.add(rendered);
        }
      }
    }

    return String.join("\n", lines);
  }

  String renderTaskItem(TaskItem node, RendererState context, BlockRecursion recursion) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var content = recursion.renderInlineNodes(node.content(), context, false);
    var prefix = checklistPrefix(context, checked);
    if (content.isBlank()) {
      return prefix.stripTrailing();
    }
    return prefix + content;
  }

  String renderBlockTaskItem(BlockTaskItem node, RendererState context, BlockRecursion recursion) {
    return String.join("\n", renderBlockTaskItemLines(node, context, recursion));
  }

  List<String> renderBlockTaskItemLines(
      BlockTaskItem node, RendererState context, BlockRecursion recursion) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var prefix = checklistPrefix(context, checked);
    var blocks = node.content();
    if (blocks.isEmpty()) {
      return List.of(prefix.stripTrailing());
    }

    var first = blocks.getFirst();
    var lines = new ArrayList<String>();
    if (first instanceof Paragraph paragraph) {
      // Checkbox markers are 2 wide, so keep the depth-based 2-space continuation indent.
      lines.addAll(
          prefixParagraph(
              prefix,
              recursion.renderInlineNodes(paragraph.content(), context, false),
              RenderBuffer.LIST_INDENT.repeat(context.listDepth() + 1)));
    } else {
      lines.add(prefix.stripTrailing());
      lines.addAll(indentedBlock(first, context, recursion));
    }

    for (var index = 1; index < blocks.size(); index++) {
      lines.add("");
      lines.addAll(indentedBlock(blocks.get(index), context, recursion));
    }

    return lines;
  }

  private List<String> indentedBlock(AdfBlock block, RendererState context, BlockRecursion recursion) {
    return RenderBuffer.indentLines(
        RenderBuffer.joinBlocks(
            recursion.renderBlock(block, context.withListDepth(context.listDepth() + 1))),
        context.listDepth() + 1,
        RenderBuffer.LIST_INDENT);
  }

  String renderBulletList(BulletList node, RendererState context, BlockRecursion recursion) {
    return renderBulletList(node, context, recursion, "");
  }

  String renderOrderedList(OrderedList node, RendererState context, BlockRecursion recursion) {
    return renderOrderedList(node, context, recursion, "");
  }

  // parentIndent: whitespace shared by this list's markers ("" at top level), so nested lists track
  // the parent's actual marker width rather than a fixed 2 per depth ("10. " is 4 wide).
  private String renderBulletList(
      BulletList node, RendererState context, BlockRecursion recursion, String parentIndent) {
    return renderListItems(node.content(), context, recursion, false, 1, parentIndent);
  }

  private String renderOrderedList(
      OrderedList node, RendererState context, BlockRecursion recursion, String parentIndent) {
    return renderListItems(node.content(), context, recursion, true, node.order(), parentIndent);
  }

  private String renderListItems(
      List<ListItem> items,
      RendererState context,
      BlockRecursion recursion,
      boolean ordered,
      int start,
      String parentIndent) {
    if (items.isEmpty()) {
      return "";
    }

    return IntStream.range(0, items.size())
        .mapToObj(
            index -> renderListItem(
                items.get(index),
                context,
                recursion,
                ordered,
                ordered ? start + index : null,
                parentIndent))
        .flatMap(List::stream)
        .collect(Collectors.joining("\n"));
  }

  private List<String> renderListItem(
      ListItem node,
      RendererState context,
      BlockRecursion recursion,
      boolean ordered,
      Integer number,
      String parentIndent) {
    var marker = ordered && number != null ? number + "." : "-";
    var prefix = parentIndent + marker + " ";
    // This item's content column: parentIndent + width of "marker + ' '" (so "10. " gives 4).
    var childIndent = parentIndent + " ".repeat(marker.length() + 1);

    var children = node.content();
    if (children.isEmpty()) {
      return List.of(prefix.stripTrailing());
    }

    var first = children.getFirst();
    var lines = new ArrayList<String>();
    if (first instanceof Paragraph paragraph) {
      lines.addAll(
          prefixParagraph(
              prefix,
              // First paragraph is at the content column (block start), so escape leading markers.
              recursion.renderInlineNodes(paragraph.content(), context, true),
              childIndent));
    } else {
      lines.add(prefix.stripTrailing());
      lines.addAll(renderListItemBlock(first, context, recursion, childIndent));
    }

    for (var index = 1; index < children.size(); index++) {
      var block = children.get(index);
      // Nested sublists stay tight; any other continuation block needs a blank line so it isn't
      // soft-wrapped into the previous paragraph.
      if (!isNestedListBlock(block)) {
        lines.add("");
      }
      lines.addAll(renderListItemBlock(block, context, recursion, childIndent));
    }

    return lines;
  }

  // List blocks nest as tight, marker-aligned sub-lists; other continuation blocks get a blank line.
  // An ordered list starting != 1 is the exception: CommonMark only lets it interrupt a paragraph
  // tightly when it starts at 1, so otherwise it needs the blank line or it (and its order) are lost.
  private static boolean isNestedListBlock(AdfBlock block) {
    return block instanceof BulletList
        || (block instanceof OrderedList orderedList && orderedList.order() == 1)
        || block instanceof TaskList
        || block instanceof DecisionList;
  }

  private List<String> renderListItemBlock(
      AdfBlock block, RendererState context, BlockRecursion recursion, String childIndent) {
    // Depth still increments for inner logic; indentation comes from childIndent, not depth.
    var childContext = context.withListDepth(context.listDepth() + 1);

    if (block instanceof BulletList bulletList) {
      var nested = renderBulletList(bulletList, childContext, recursion, childIndent);
      return nested.isBlank() ? List.of() : MarkdownText.splitLines(nested);
    }
    if (block instanceof OrderedList orderedList) {
      var nested = renderOrderedList(orderedList, childContext, recursion, childIndent);
      return nested.isBlank() ? List.of() : MarkdownText.splitLines(nested);
    }
    // childIndent already encodes the content column, so render at depth 0 and indent once.
    if (block instanceof TaskList taskList) {
      var nested = renderTaskList(taskList, context.withListDepth(0), recursion);
      return nested.isBlank() ? List.of() : RenderBuffer.indentLines(nested, childIndent);
    }
    if (block instanceof DecisionList decisionList) {
      var nested = renderDecisionList(decisionList, context.withListDepth(0), recursion);
      return nested.isBlank() ? List.of() : RenderBuffer.indentLines(nested, childIndent);
    }

    // Known limitation: a list nested inside a non-list block (e.g. a panel) re-enters via
    // renderBlock and can't receive childIndent, falling back to depth-based indent.
    var text = RenderBuffer.joinBlocks(recursion.renderBlock(block, childContext));
    return RenderBuffer.indentLines(text, childIndent);
  }

  String renderDecisionList(DecisionList node, RendererState context, BlockRecursion recursion) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      lines.add(renderDecisionItem(item, context, recursion));
    }
    return String.join("\n", lines);
  }

  String renderDecisionItem(DecisionItem node, RendererState context, BlockRecursion recursion) {
    var state = node.state();
    var label = MarkdownText.labelToken(
        state == null || state.isBlank() ? "decision" : "decision:" + state,
        context.escapeParentheses());
    var content = recursion.renderInlineNodes(node.content(), context, false);
    var prefix = RenderBuffer.LIST_INDENT.repeat(Math.max(0, context.listDepth())) + "- ";
    if (content.isBlank()) {
      return prefix + label;
    }
    return prefix + label + " " + content;
  }

  private String checklistPrefix(RendererState context, boolean checked) {
    var indent = RenderBuffer.LIST_INDENT.repeat(Math.max(0, context.listDepth()));
    return indent + "- [" + (checked ? "x" : " ") + "] ";
  }

  // continuationIndent aligns the wrapped lines of the first paragraph to the item's content column.
  private List<String> prefixParagraph(String prefix, String text, String continuationIndent) {
    if (text.isBlank()) {
      return List.of(prefix.stripTrailing());
    }

    var parts = MarkdownText.splitLines(text);
    var lines = new ArrayList<String>(parts.size());
    lines.add(prefix + parts.getFirst());
    for (var index = 1; index < parts.size(); index++) {
      lines.add(continuationIndent + parts.get(index));
    }
    return lines;
  }
}
