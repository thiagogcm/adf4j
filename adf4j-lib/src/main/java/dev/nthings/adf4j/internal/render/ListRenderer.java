package dev.nthings.adf4j.internal.render;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

final class ListRenderer {

  String renderTaskList(TaskList node, RendererState context, BlockRecursion recursion) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    var previousWasNested = false;
    for (var item : node.content()) {
      if (item instanceof TaskItem taskItem) {
        lines.add(renderTaskItem(taskItem, context, recursion));
        previousWasNested = false;
      } else if (item instanceof BlockTaskItem blockTaskItem) {
        lines.addAll(renderBlockTaskItemLines(blockTaskItem, context, recursion));
        previousWasNested = false;
      } else if (item instanceof TaskList nested) {
        var rendered =
            RenderBuffer.indentLines(
                renderTaskList(nested, context, recursion), RenderBuffer.LIST_INDENT);
        if (!rendered.isEmpty()) {
          if (previousWasNested) {
            lines.add("");
            lines.add(RenderBuffer.LIST_INDENT + "<!-- -->");
            lines.add("");
          }
          lines.addAll(rendered);
          previousWasNested = true;
        }
      }
    }

    return String.join("\n", lines);
  }

  String renderTaskItem(TaskItem node, RendererState context, BlockRecursion recursion) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var content = recursion.renderInlineNodes(node.content(), context, false);
    return String.join(
        "\n", prefixParagraph(checklistPrefix(checked), content, RenderBuffer.LIST_INDENT));
  }

  String renderBlockTaskItem(BlockTaskItem node, RendererState context, BlockRecursion recursion) {
    return String.join("\n", renderBlockTaskItemLines(node, context, recursion));
  }

  List<String> renderBlockTaskItemLines(
      BlockTaskItem node, RendererState context, BlockRecursion recursion) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var prefix = checklistPrefix(checked);
    var blocks = node.content();
    if (blocks.isEmpty()) {
      return List.of(prefix.stripTrailing());
    }

    var first = blocks.getFirst();
    var lines = new ArrayList<String>();
    if (first instanceof Paragraph paragraph) {
      // The checkbox belongs to the paragraph; only the bullet determines its content column.
      lines.addAll(
          prefixParagraph(
              prefix,
              recursion.renderInlineNodes(paragraph.content(), context, false),
              RenderBuffer.LIST_INDENT));
    } else {
      lines.add(prefix.stripTrailing());
      if (first instanceof OrderedList orderedList && orderedList.order() != 1) {
        lines.add("");
      }
      lines.addAll(indentedBlock(first, context, recursion, RenderBuffer.LIST_INDENT));
    }

    var previous = first;
    for (var index = 1; index < blocks.size(); index++) {
      var block = blocks.get(index);
      var rendered = indentedBlock(block, context, recursion, RenderBuffer.LIST_INDENT);
      if (rendered.isEmpty()) {
        continue;
      }
      lines.add("");
      if (needsListSeparator(previous, block)) {
        lines.add(RenderBuffer.LIST_INDENT + "<!-- -->");
        lines.add("");
      }
      lines.addAll(rendered);
      previous = block;
    }

    return lines;
  }

  String renderBulletList(BulletList node, RendererState context, BlockRecursion recursion) {
    return renderListItems(node.content(), context, recursion, false, 1);
  }

  String renderOrderedList(OrderedList node, RendererState context, BlockRecursion recursion) {
    return renderListItems(node.content(), context, recursion, true, node.order());
  }

  private String renderListItems(
      List<ListItem> items,
      RendererState context,
      BlockRecursion recursion,
      boolean ordered,
      int start) {
    if (items.isEmpty()) {
      return "";
    }

    return IntStream.range(0, items.size())
        .mapToObj(
            index ->
                renderListItem(
                    items.get(index), context, recursion, ordered, ordered ? start + index : null))
        .flatMap(List::stream)
        .collect(Collectors.joining("\n"));
  }

  private List<String> renderListItem(
      ListItem node,
      RendererState context,
      BlockRecursion recursion,
      boolean ordered,
      @Nullable Integer number) {
    var marker = ordered && number != null ? number + "." : "-";
    var prefix = marker + " ";
    // Each subtree renders locally. Its parent adds the marker's width exactly once.
    var childIndent = " ".repeat(marker.length() + 1);

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
      lines.addAll(indentedBlock(first, context, recursion, childIndent));
    }

    var previous = first;
    for (var index = 1; index < children.size(); index++) {
      var block = children.get(index);
      var rendered = indentedBlock(block, context, recursion, childIndent);
      if (rendered.isEmpty()) {
        continue;
      }
      // Nested sublists stay tight; any other continuation block needs a blank line so it isn't
      // soft-wrapped into the previous paragraph.
      if (needsListSeparator(previous, block)) {
        lines.add("");
        lines.add(childIndent + "<!-- -->");
        lines.add("");
      } else if (!isNestedListBlock(block)) {
        lines.add("");
      }
      lines.addAll(rendered);
      previous = block;
    }

    return lines;
  }

  // List blocks nest as tight, marker-aligned sub-lists; other continuation blocks get a blank
  // line.
  // An ordered list starting != 1 is the exception: CommonMark only lets it interrupt a paragraph
  // tightly when it starts at 1, so otherwise it needs the blank line or it (and its order) are
  // lost.
  private static boolean isNestedListBlock(AdfBlock block) {
    return block instanceof BulletList
        || (block instanceof OrderedList orderedList && orderedList.order() == 1)
        || block instanceof TaskList
        || block instanceof DecisionList;
  }

  // Blank lines make a Markdown list loose; they do not start a separate list of the same kind.
  static boolean needsListSeparator(@Nullable AdfBlock previous, AdfBlock next) {
    var kind = listKind(previous);
    return kind != 0 && kind == listKind(next);
  }

  private static int listKind(@Nullable AdfBlock block) {
    return switch (block) {
      case OrderedList _ -> 1;
      case BulletList _, TaskList _, DecisionList _ -> 2;
      case null, default -> 0;
    };
  }

  private List<String> indentedBlock(
      AdfBlock block, RendererState context, BlockRecursion recursion, String childIndent) {
    var text = RenderBuffer.joinBlocks(recursion.renderBlock(block, context));
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
    var label =
        MarkdownText.labelToken(
            state == null || state.isBlank() ? "decision" : "decision:" + state,
            context.escapeParentheses());
    var content = recursion.renderInlineNodes(node.content(), context, false);
    return String.join(
        "\n", prefixParagraph("- " + label + " ", content, RenderBuffer.LIST_INDENT));
  }

  private String checklistPrefix(boolean checked) {
    return "- [" + (checked ? "x" : " ") + "] ";
  }

  // continuationIndent aligns the wrapped lines of the first paragraph to the item's content
  // column.
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
