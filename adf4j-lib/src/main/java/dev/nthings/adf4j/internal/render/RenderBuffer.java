package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


final class RenderBuffer {

  static final String LIST_INDENT = "  ";

  private RenderBuffer() {
  }

  static String joinBlocks(List<String> source) {
    if (source == null || source.isEmpty()) {
      return "";
    }

    var blocks = source.stream()
        .filter(Objects::nonNull)
        .map(String::stripTrailing)
        .filter(block -> !block.isEmpty())
        .toList();
    return String.join("\n\n", blocks);
  }

  static List<String> indentLines(String value, int depth, String indentUnit) {
    if (value == null || value.isBlank()) {
      return List.of();
    }

    var indent = indentUnit.repeat(Math.max(0, depth));
    var lines = new ArrayList<String>();
    for (var line : MarkdownText.splitLines(value)) {
      lines.add(line.isBlank() ? indent.stripTrailing() : indent + line);
    }
    return lines;
  }
}
