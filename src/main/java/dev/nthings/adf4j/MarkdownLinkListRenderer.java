package dev.nthings.adf4j;

import java.util.ArrayList;
import java.util.List;

import dev.nthings.adf4j.internal.MarkdownText;

public final class MarkdownLinkListRenderer {

  private static final String CHILD_INDENT = "  ";

  private MarkdownLinkListRenderer() {
  }

  public record LinkNode(String label, String href, List<LinkNode> children) {

    public LinkNode {
      children = children == null ? List.of() : List.copyOf(children);
    }
  }

  public static String render(List<LinkNode> nodes, Integer maxDepth) {
    return render(nodes, maxDepth, 0);
  }

  private static String render(List<LinkNode> nodes, Integer maxDepth, int depth) {
    if (nodes == null || nodes.isEmpty()) {
      return "";
    }
    if (maxDepth != null && depth >= maxDepth) {
      return "";
    }

    var lines = new ArrayList<String>();
    for (var node : nodes) {
      if (node == null || node.label() == null || node.label().isBlank() || node.href() == null
          || node.href().isBlank()) {
        continue;
      }

      lines.add(
          CHILD_INDENT.repeat(depth)
              + "- ["
              + MarkdownText.escapeLinkText(node.label())
              + "]("
              + node.href()
              + ")");
      var nested = render(node.children(), maxDepth, depth + 1);
      if (!nested.isBlank()) {
        lines.add(nested);
      }
    }

    return String.join("\n", lines);
  }
}
