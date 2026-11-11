package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class MarkdownBlocks {

  static final String LIST_INDENT = "  ";

  /// Invisible comment keeping adjacent same-kind lists apart (a blank line would merge them).
  static final String LIST_SEPARATOR = "<!-- -->";

  private static final Pattern ORDERED_MARKER = Pattern.compile("\\d+\\.(?: |$)");

  private enum ListKind {
    NONE,
    ORDERED,
    BULLET;

    // Outside pipe cells, literal markers are escaped, so a column-0 marker is a real list.
    static ListKind ofLine(String line) {
      if (line.equals("-") || line.startsWith("- ")) {
        return BULLET;
      }
      return ORDERED_MARKER.matcher(line).lookingAt() ? ORDERED : NONE;
    }
  }

  private MarkdownBlocks() {}

  /// Non-blank blocks, right-trimmed, blank-line separated; a {@link #LIST_SEPARATOR} sits
  /// between same-kind lists that would otherwise merge.
  static String join(List<String> source) {
    var blocks = new ArrayList<String>();
    @Nullable String previous = null;
    for (var block : source) {
      var stripped = block.stripTrailing();
      if (stripped.isEmpty()) {
        continue;
      }
      if (previous != null && needsListSeparator(previous, stripped)) {
        blocks.add(LIST_SEPARATOR);
      }
      blocks.add(stripped);
      previous = stripped;
    }
    return String.join("\n\n", blocks);
  }

  /// Like {@link #join} for a pipe cell: one line, so no lists to separate.
  static String joinWithoutLists(List<String> source) {
    return String.join(
        "\n\n",
        source.stream().map(String::stripTrailing).filter(block -> !block.isEmpty()).toList());
  }

  /// Whether two blocks joined by a blank line would merge into one list. Reads the Markdown, so it
  /// sees through transparent containers and list-producing macros.
  static boolean needsListSeparator(String previous, String next) {
    var newline = next.indexOf('\n');
    var kind = ListKind.ofLine(newline < 0 ? next : next.substring(0, newline));
    return kind != ListKind.NONE && kind == closingListKind(previous);
  }

  // Nested content is indented, so column 0 of the last line gives the closing kind.
  private static ListKind closingListKind(String block) {
    var end = block.length();
    while (end > 0) {
      var start = block.lastIndexOf('\n', end - 1) + 1;
      if (start < end && block.charAt(start) != ' ') {
        return ListKind.ofLine(block.substring(start, end));
      }
      end = start - 1;
    }
    return ListKind.NONE;
  }

  /// Lines of `value` under `indent`; empty when blank.
  static List<String> indentLines(String value, String indent) {
    if (value.isBlank()) {
      return List.of();
    }

    var lines = new ArrayList<String>();
    for (var line : MarkdownText.splitLines(value)) {
      lines.add(indentLine(line, indent));
    }
    return lines;
  }

  /// One line under `indent`; blank lines get no trailing spaces.
  static String indentLine(String line, String indent) {
    return line.isBlank() ? indent.stripTrailing() : indent + line;
  }
}
