package dev.nthings.adf4j.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.nthings.adf4j.internal.MarkdownText;
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

public final class ListRenderer {

  String renderTaskList(TaskList node, RenderContext context, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      if (item instanceof TaskItem taskItem) {
        lines.add(renderTaskItem(taskItem, context, adfRenderer));
      } else if (item instanceof BlockTaskItem blockTaskItem) {
        lines.addAll(renderBlockTaskItemLines(blockTaskItem, context, adfRenderer));
      }
    }

    return String.join("\n", lines);
  }

  String renderTaskItem(TaskItem node, RenderContext context, AdfRenderer adfRenderer) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var content = adfRenderer.renderInlineNodes(node.content(), context);
    var prefix = checklistPrefix(context, checked);
    if (content.isBlank()) {
      return prefix.stripTrailing();
    }
    return prefix + content;
  }

  String renderBlockTaskItem(BlockTaskItem node, RenderContext context, AdfRenderer adfRenderer) {
    return String.join("\n", renderBlockTaskItemLines(node, context, adfRenderer));
  }

  List<String> renderBlockTaskItemLines(
      BlockTaskItem node, RenderContext context, AdfRenderer adfRenderer) {
    var checked = "DONE".equalsIgnoreCase(node.state());
    var prefix = checklistPrefix(context, checked);
    var blocks = node.content();
    if (blocks.isEmpty()) {
      return List.of(prefix.stripTrailing());
    }

    var first = blocks.getFirst();
    var lines = new ArrayList<String>();
    if (first instanceof Paragraph paragraph) {
      lines.addAll(
          prefixParagraph(
              prefix,
              adfRenderer.renderInlineNodes(paragraph.content(), context),
              context.listDepth() + 1));
    } else {
      lines.add(prefix.stripTrailing());
      lines.addAll(indentedBlock(first, context, adfRenderer));
    }

    for (var index = 1; index < blocks.size(); index++) {
      lines.add("");
      lines.addAll(indentedBlock(blocks.get(index), context, adfRenderer));
    }

    return lines;
  }

  private List<String> indentedBlock(AdfBlock block, RenderContext context, AdfRenderer adfRenderer) {
    return RenderBuffer.indentLines(
        adfRenderer.joinBlocks(
            adfRenderer.renderBlock(block, context.withListDepth(context.listDepth() + 1))),
        context.listDepth() + 1,
        RenderBuffer.LIST_INDENT);
  }

  String renderBulletList(BulletList node, RenderContext context, AdfRenderer adfRenderer) {
    return renderListItems(node.content(), context, adfRenderer, false, 1);
  }

  String renderOrderedList(OrderedList node, RenderContext context, AdfRenderer adfRenderer) {
    return renderListItems(node.content(), context, adfRenderer, true, node.order());
  }

  private String renderListItems(
      List<ListItem> items,
      RenderContext context,
      AdfRenderer adfRenderer,
      boolean ordered,
      int start) {
    if (items.isEmpty()) {
      return "";
    }

    return IntStream.range(0, items.size())
        .mapToObj(
            index -> renderListItem(
                items.get(index),
                context,
                adfRenderer,
                ordered,
                ordered ? start + index : null))
        .flatMap(List::stream)
        .collect(Collectors.joining("\n"));
  }

  List<String> renderListItem(
      ListItem node,
      RenderContext context,
      AdfRenderer adfRenderer,
      boolean ordered,
      Integer number) {
    var indent = RenderBuffer.LIST_INDENT.repeat(Math.max(0, context.listDepth()));
    var marker = ordered && number != null ? number + "." : "-";
    var prefix = indent + marker + " ";

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
              adfRenderer.renderInlineNodes(paragraph.content(), context),
              context.listDepth() + 1));
    } else {
      lines.add(prefix.stripTrailing());
      lines.addAll(renderListItemBlock(first, context, adfRenderer));
    }

    for (var index = 1; index < children.size(); index++) {
      lines.addAll(renderListItemBlock(children.get(index), context, adfRenderer));
    }

    return lines;
  }

  List<String> renderListItemBlock(AdfBlock block, RenderContext context, AdfRenderer adfRenderer) {
    var childContext = context.withListDepth(context.listDepth() + 1);

    if (block instanceof BulletList bulletList) {
      var nested = renderBulletList(bulletList, childContext, adfRenderer);
      return nested.isBlank() ? List.of() : MarkdownText.splitLines(nested);
    }
    if (block instanceof OrderedList orderedList) {
      var nested = renderOrderedList(orderedList, childContext, adfRenderer);
      return nested.isBlank() ? List.of() : MarkdownText.splitLines(nested);
    }

    var text = adfRenderer.joinBlocks(adfRenderer.renderBlock(block, childContext));
    if (text.isBlank()) {
      return List.of();
    }

    var indent = RenderBuffer.LIST_INDENT.repeat(context.listDepth() + 1);
    var lines = new ArrayList<String>();
    for (var line : MarkdownText.splitLines(text)) {
      lines.add(line.isBlank() ? indent.stripTrailing() : indent + line);
    }
    return lines;
  }

  String renderDecisionList(DecisionList node, RenderContext context, AdfRenderer adfRenderer) {
    if (node.content().isEmpty()) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var item : node.content()) {
      lines.add(renderDecisionItem(item, context, adfRenderer));
    }
    return String.join("\n", lines);
  }

  String renderDecisionItem(DecisionItem node, RenderContext context, AdfRenderer adfRenderer) {
    var state = node.state();
    var label = state == null || state.isBlank() ? "[decision]" : "[decision:%s]".formatted(state);
    var content = adfRenderer.renderInlineNodes(node.content(), context);
    var prefix = RenderBuffer.LIST_INDENT.repeat(Math.max(0, context.listDepth())) + "- ";
    if (content.isBlank()) {
      return prefix + label;
    }
    return prefix + label + " " + content;
  }

  private String checklistPrefix(RenderContext context, boolean checked) {
    var indent = RenderBuffer.LIST_INDENT.repeat(Math.max(0, context.listDepth()));
    return indent + "- [" + (checked ? "x" : " ") + "] ";
  }

  private List<String> prefixParagraph(String prefix, String text, int indentDepth) {
    if (text.isBlank()) {
      return List.of(prefix.stripTrailing());
    }

    var parts = MarkdownText.splitLines(text);
    var lines = new ArrayList<String>(parts.size());
    lines.add(prefix + parts.getFirst());
    var indent = RenderBuffer.LIST_INDENT.repeat(indentDepth);
    for (var index = 1; index < parts.size(); index++) {
      lines.add(indent + parts.get(index));
    }
    return lines;
  }
}
