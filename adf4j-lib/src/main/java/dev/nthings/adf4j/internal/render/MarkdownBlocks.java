package dev.nthings.adf4j.internal.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Layout rules for rendered blocks: how they join and how they indent under a list item.
final class MarkdownBlocks {

  static final String LIST_INDENT = "  ";

  /// An invisible HTML comment that keeps two adjacent lists of the same Markdown kind apart: blank
  /// lines make a list loose, they do not start a new one.
  static final String LIST_SEPARATOR = "<!-- -->";

  private static final Pattern ORDERED_MARKER = Pattern.compile("\\d+\\.(?: |$)");

  private enum ListKind {
    NONE,
    ORDERED,
    BULLET;

    // Outside a pipe cell literal text has its markers escaped, so one at column 0 is a list,
    // whichever node wrote it.
    static ListKind ofLine(String line) {
      if (line.equals("-") || line.startsWith("- ")) {
        return BULLET;
      }
      return ORDERED_MARKER.matcher(line).lookingAt() ? ORDERED : NONE;
    }
  }

  private MarkdownBlocks() {}

  /// The non-blank blocks, right-trimmed and separated by a blank line, with a
  /// {@link #LIST_SEPARATOR} wherever a block closing with a list meets one opening with a list of
  /// the same kind.
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

  /// The non-blank blocks, right-trimmed and separated by a blank line, for a pipe cell: it is one
  /// line, so it holds no lists to keep apart, and its text keeps its markers unescaped.
  static String joinWithoutLists(List<String> source) {
    return String.join(
        "\n\n",
        source.stream().map(String::stripTrailing).filter(block -> !block.isEmpty()).toList());
  }

  /// Whether two rendered blocks, joined by a blank line, would merge into one Markdown list. This
  /// reads the Markdown rather than the AST so that it sees through the transparent containers
  /// (layouts, excerpts) and every macro that expands to a list.
  static boolean needsListSeparator(String previous, String next) {
    var newline = next.indexOf('\n');
    var kind = ListKind.ofLine(newline < 0 ? next : next.substring(0, newline));
    return kind != ListKind.NONE && kind == closingListKind(previous);
  }

  // Everything nested in a list item is indented, so a block's last line at column 0 tells what it
  // closes with.
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

  /// The lines of `value` under `indent`, with blank lines kept free of trailing spaces; empty for
  /// a blank value.
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

  /// One line under `indent`, a blank one kept free of trailing spaces.
  static String indentLine(String line, String indent) {
    return line.isBlank() ? indent.stripTrailing() : indent + line;
  }
}
