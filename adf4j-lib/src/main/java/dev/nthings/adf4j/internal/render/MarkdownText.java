package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.internal.Strings;
import dev.nthings.adf4j.internal.UrlSafety;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class MarkdownText {

  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");

  // Both ordered-list delimiters; ")" reaches here only when parentheses are not escaped.
  private static final Pattern LEADING_ORDERED_MARKER = Pattern.compile("^(\\d+)([.)])");

  // Markdown permits web destinations and the library's inert resource placeholders.
  private static final UrlSafety.SchemePolicy URL_SCHEMES =
      UrlSafety.SchemePolicy.of(
          "http", "https", "mailto", "tel", "ftp", "ftps", "media", "attachment");

  private MarkdownText() {}

  public static List<String> splitLines(String value) {
    return Arrays.asList(LINE_BREAK_PATTERN.split(value, -1));
  }

  /// Whether text appended to `text` opens a line: only whitespace after the last break, or no
  /// break and `startedAtLineStart`.
  public static boolean endsAtLineStart(CharSequence text, boolean startedAtLineStart) {
    for (var i = text.length() - 1; i >= 0; i--) {
      var c = text.charAt(i);
      if (c == '\n') {
        return true;
      }
      if (!Character.isWhitespace(c)) {
        return false;
      }
    }
    return startedAtLineStart;
  }

  /// A literal `[inner]` label token, fully inline-escaped so it can't parse as a link.
  public static String labelToken(String inner, boolean escapeParentheses) {
    return escapeInlineText("[" + inner + "]", false, escapeParentheses);
  }

  /// Link `[label](url)` from raw operands: inline-escaped label, sanitized destination.
  public static String link(@Nullable String label, String url, boolean escapeParentheses) {
    return "["
        + escapeInlineText(label, false, escapeParentheses)
        + "]("
        + escapeUrlDestination(url)
        + ")";
  }

  /// Like {@link #link} with an already-rendered label, emitted verbatim.
  public static String linkRendered(String label, String url) {
    return "[" + label + "](" + escapeUrlDestination(url) + ")";
  }

  /// Like {@link #linkRendered} with a one-line, quote-escaped title.
  public static String linkRendered(String label, String url, String title) {
    return "[" + label + "](" + escapeUrlDestination(url) + " \"" + escapeLinkTitle(title) + "\")";
  }

  // A newline inside the title would break the parse.
  private static String escapeLinkTitle(String title) {
    return Strings.collapseLineBreaks(title).replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /// Fenced code block; the fence outlasts any backtick run in `content` (minimum three).
  public static String fencedCodeBlock(String content, @Nullable String language) {
    var ticks = "`".repeat(Math.max(3, longestBacktickRun(content) + 1));
    // One clean token: backticks or newlines would break the fence.
    var info =
        language == null ? "" : Strings.collapseLineBreaks(language.replace("`", "")).strip();
    var openingFence = (ticks + info).stripTrailing();
    return "%s\n%s\n%s".formatted(openingFence, content, ticks).stripTrailing();
  }

  /// Inline code span whose fence outlasts the longest backtick run. Pads a space each side when
  /// the content borders a backtick (CommonMark strips one).
  public static String inlineCodeSpan(String content) {
    var fence = "`".repeat(longestBacktickRun(content) + 1);
    var needsPadding =
        !content.isEmpty()
            && (content.charAt(0) == '`' || content.charAt(content.length() - 1) == '`');
    return needsPadding ? fence + " " + content + " " + fence : fence + content + fence;
  }

  public static int longestBacktickRun(String value) {
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

  /// Backslash-escapes CommonMark inline punctuation in literal text, and neutralises a leading
  /// block marker per line: the first line only when `atLineStart`, later lines always. An
  /// intra-word `_` stays literal (never emphasis in CommonMark). `(`/`)` are escaped only when
  /// `escapeParentheses`; a leading "1)" is neutralised regardless. Null is empty.
  public static String escapeInlineText(
      @Nullable String text, boolean atLineStart, boolean escapeParentheses) {
    var value = Objects.requireNonNullElse(text, "");
    if (value.isEmpty()) {
      return value;
    }

    var escaped = escapeInlinePunctuation(value, escapeParentheses);
    if (escaped.indexOf('\n') < 0 && escaped.indexOf('\r') < 0) {
      return atLineStart ? neutralizeLeadingBlock(escaped) : escaped;
    }

    // Each line after a break starts at column 0; neutralise each (endings normalise to \n).
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

  // One pass; allocates only when something is escaped. An intra-word '_' stays literal.
  private static String escapeInlinePunctuation(String value, boolean escapeParentheses) {
    StringBuilder escaped = null;
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      var escape =
          c == '_' ? !isIntraWordUnderscore(value, i) : isInlinePunctuation(c, escapeParentheses);
      if (escape) {
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

  // An '_' with a word character on each side can neither open nor close emphasis.
  private static boolean isIntraWordUnderscore(String value, int i) {
    return i > 0
        && isWordChar(value.charAt(i - 1))
        && i + 1 < value.length()
        && isWordChar(value.charAt(i + 1));
  }

  private static boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c);
  }

  private static boolean isInlinePunctuation(char c, boolean escapeParentheses) {
    return switch (c) {
      case '\\', '`', '*', '[', ']', '~', '<', '&', '!' -> true;
      // Parentheses parse specially only in link destinations, so escaping is opt-in.
      case '(', ')' -> escapeParentheses;
      default -> false;
    };
  }

  // Input is already inline-escaped; at most one leading construct applies.
  private static String neutralizeLeadingBlock(String s) {
    if (s.isEmpty()) {
      return s;
    }

    // Leading tab or 4+ spaces is indented code; break the run with &#32;.
    if (s.charAt(0) == '\t') {
      return "&#32;" + s.substring(1);
    }
    var lead = countLeadingSpaces(s);
    if (lead >= 4) {
      return "&#32;" + s.substring(1);
    }

    // Up to 3 leading spaces may precede a block marker; check at the first non-space.
    var prefix = s.substring(0, lead);
    var rest = s.substring(lead);
    if (rest.isEmpty()) {
      return s;
    }

    // '*' is already inline-escaped; only '-' and '+' remain.
    var first = rest.charAt(0);
    if (first == '#' || first == '>' || first == '-' || first == '+') {
      return prefix + "\\" + rest;
    }
    // Escape whichever delimiter matched, so the digits can't open a list.
    var ordered = LEADING_ORDERED_MARKER.matcher(rest);
    if (ordered.find()) {
      var digits = ordered.group(1);
      var delimiter = ordered.group(2);
      return prefix + digits + "\\" + delimiter + rest.substring(ordered.end());
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

  /// Backslash-escapes `\`, `[`, `]` in alt text, and `(`, `)` when `escapeParentheses`.
  public static String escapeAltText(String alt, boolean escapeParentheses) {
    var escaped = alt.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    return escapeParentheses ? escaped.replace("(", "\\(").replace(")", "\\)") : escaped;
  }

  /// Makes a URL safe in a `(...)` destination. An unsafe scheme (javascript:, data:, …) is
  /// defused first. The URL is then kept when clean, wrapped as `<url>` when it has a space,
  /// control char or unbalanced parens, or percent-encoded when angle brackets or newlines rule
  /// out wrapping. Blank is unchanged.
  public static String escapeUrlDestination(String url) {
    if (url.isBlank()) {
      return url;
    }

    url = UrlSafety.sanitizeScheme(url, URL_SCHEMES);

    var hasAngleOrNewline =
        url.indexOf('<') >= 0
            || url.indexOf('>') >= 0
            || url.indexOf('\n') >= 0
            || url.indexOf('\r') >= 0;

    if (!hasAngleOrNewline) {
      if (hasSpaceOrControl(url) || hasUnbalancedParens(url)) {
        return "<" + url + ">";
      }
      return url;
    }

    // No angle-wrapping here (URL has '<', '>' or a newline): percent-encode the breakers.
    return url.replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
        .replace("<", "%3C")
        .replace(">", "%3E")
        .replace("\n", "%0A")
        .replace("\r", "%0D");
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
