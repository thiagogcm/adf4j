package dev.nthings.adf4j.internal.render;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class MarkdownText {

  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");

  private static final Pattern LEADING_ORDERED_MARKER = Pattern.compile("^(\\d+)\\.");

  private MarkdownText() {
  }

  public static int clampHeadingLevel(int level) {
    return Math.clamp(level, 1, 6);
  }

  public static String dateFromTimestamp(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }

    try {
      var value = Long.parseLong(timestamp);
      var date = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate();
      return date.toString();
    } catch (NumberFormatException _) {
      return timestamp;
    }
  }

  public static List<String> splitLines(String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.asList(LINE_BREAK_PATTERN.split(value, -1));
  }

  public static String escapeLinkText(String value) {
    return Objects.requireNonNullElse(value, "").replace("[", "\\[").replace("]", "\\]");
  }

  /** Length of the longest run of consecutive backticks in {@code value} (0 for null/empty). */
  public static int longestBacktickRun(String value) {
    if (value == null || value.isEmpty()) {
      return 0;
    }
    var longest = 0;
    var current = 0;
    for (var i = 0; i < value.length(); i++) {
      if (value.charAt(i) == '`') {
        current++;
        longest = Math.max(longest, current);
      } else {
        current = 0;
      }
    }
    return longest;
  }

  /**
   * Backslash-escapes CommonMark inline punctuation ({@code \ ` * _ [ ] ( ) ~ < &}) in literal text,
   * and neutralises a leading block marker (#, &gt;, -, +, ordered "1.", indented-code run) on each
   * line — the first line only when {@code atLineStart}, every later line unconditionally. Null is
   * treated as empty.
   */
  public static String escapeInlineText(String text, boolean atLineStart) {
    var value = Objects.requireNonNullElse(text, "");
    if (value.isEmpty()) {
      return value;
    }

    var escaped = escapeInlinePunctuation(value);
    if (escaped.indexOf('\n') < 0 && escaped.indexOf('\r') < 0) {
      return atLineStart ? neutralizeLeadingBlock(escaped) : escaped;
    }

    // Each line after an embedded break starts at column 0, so neutralise every line's leading
    // marker (line endings normalise to \n).
    var lines = LINE_BREAK_PATTERN.split(escaped, -1);
    var result = new StringBuilder(escaped.length() + 8);
    for (var i = 0; i < lines.length; i++) {
      if (i > 0) {
        result.append('\n');
      }
      result.append(i == 0 && !atLineStart ? lines[i] : neutralizeLeadingBlock(lines[i]));
    }
    return result.toString();
  }

  // Backslash-escapes CommonMark inline punctuation in one pass, allocating only when an escapable
  // character is present (the common no-special-char case returns value unchanged).
  private static String escapeInlinePunctuation(String value) {
    StringBuilder escaped = null;
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      if (isInlinePunctuation(c)) {
        if (escaped == null) {
          escaped = new StringBuilder(value.length() + 8).append(value, 0, i);
        }
        escaped.append('\\');
      }
      if (escaped != null) {
        escaped.append(c);
      }
    }
    return escaped == null ? value : escaped.toString();
  }

  private static boolean isInlinePunctuation(char c) {
    return switch (c) {
      case '\\', '`', '*', '_', '[', ']', '(', ')', '~', '<', '&' -> true;
      default -> false;
    };
  }

  // Input is already inline-escaped; at most one leading construct applies.
  private static String neutralizeLeadingBlock(String s) {
    if (s.isEmpty()) {
      return s;
    }

    // Leading tab or 4+ spaces = indented code; break the run by emitting the first space as &#32;.
    if (s.charAt(0) == '\t') {
      return "&#32;" + s.substring(1);
    }
    var lead = countLeadingSpaces(s);
    if (lead >= 4) {
      return "&#32;" + s.substring(1);
    }

    // Up to 3 leading spaces still permit a block marker, so neutralise at the first non-space char.
    var prefix = s.substring(0, lead);
    var rest = s.substring(lead);
    if (rest.isEmpty()) {
      return s;
    }

    // '*' bullets are already inline-escaped, so only '-' and '+' remain to handle here.
    var first = rest.charAt(0);
    if (first == '#' || first == '>' || first == '-' || first == '+') {
      return prefix + "\\" + rest;
    }
    // Ordered marker: escape the dot ("1)" is already safe since ')' is inline-escaped).
    var ordered = LEADING_ORDERED_MARKER.matcher(rest);
    if (ordered.find()) {
      var digits = ordered.group(1);
      return prefix + digits + "\\." + rest.substring(ordered.end());
    }

    return s;
  }

  private static int countLeadingSpaces(String s) {
    var count = 0;
    while (count < s.length() && s.charAt(count) == ' ') {
      count++;
    }
    return count;
  }

  /** Backslash-escapes {@code [ ] ( )} in image alt text. Null is treated as empty. */
  public static String escapeAltText(String alt) {
    return escapeLinkText(alt).replace("(", "\\(").replace(")", "\\)");
  }

  /**
   * Makes a URL safe inside a markdown {@code (...)} destination: returned unchanged when clean,
   * wrapped as {@code <url>} when it holds a space/control char or unbalanced parentheses, or with
   * space/parens percent-encoded when angle-wrapping is unavailable. Null/blank is returned
   * unchanged.
   */
  public static String escapeUrlDestination(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }

    var hasAngleOrNewline = url.indexOf('<') >= 0 || url.indexOf('>') >= 0 || url.indexOf('\n') >= 0
        || url.indexOf('\r') >= 0;

    if (!hasAngleOrNewline) {
      if (hasSpaceOrControl(url) || hasUnbalancedParens(url)) {
        return "<" + url + ">";
      }
      return url;
    }

    return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
  }

  private static boolean hasUnbalancedParens(String url) {
    var depth = 0;
    for (var i = 0; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth < 0) {
          return true;
        }
      }
    }
    return depth != 0;
  }

  private static boolean hasSpaceOrControl(String url) {
    for (var i = 0; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c == ' ' || Character.isISOControl(c)) {
        return true;
      }
    }
    return false;
  }
}
