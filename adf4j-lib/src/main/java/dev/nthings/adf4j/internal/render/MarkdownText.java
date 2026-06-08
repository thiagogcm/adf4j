package dev.nthings.adf4j.internal.render;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class MarkdownText {

  private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");

  // Both CommonMark ordered-list delimiters, "1." and "1)" — the latter reaches here only when
  // parentheses are left unescaped (otherwise inline escaping already defused it).
  private static final Pattern LEADING_ORDERED_MARKER = Pattern.compile("^(\\d+)([.)])");

  // Schemes safe to emit as a link destination; anything else is defused by escapeUrlDestination.
  // "media"/"attachment" are the library's own inert placeholder schemes.
  private static final Set<String> SAFE_URL_SCHEMES =
      Set.of("http", "https", "mailto", "tel", "ftp", "ftps", "media", "attachment");

  private MarkdownText() {
  }

  public static List<String> splitLines(String value) {
    if (value == null) {
      return List.of();
    }
    return Arrays.asList(LINE_BREAK_PATTERN.split(value, -1));
  }

  /** Replaces every line break with a single space, reusing the shared precompiled pattern. */
  public static String collapseLineBreaks(String value) {
    return LINE_BREAK_PATTERN.matcher(value).replaceAll(" ");
  }

  /** A literal {@code [inner]} label token, fully inline-escaped so it can't parse as a link. */
  public static String labelToken(String inner, boolean escapeParentheses) {
    return escapeInlineText("[" + Objects.requireNonNullElse(inner, "") + "]", false, escapeParentheses);
  }

  /**
   * A Markdown inline link {@code [label](url)} from raw operands: the label is inline-escaped and
   * the destination is scheme-sanitized and escaped.
   */
  public static String link(String label, String url, boolean escapeParentheses) {
    return "[" + escapeInlineText(label, false, escapeParentheses) + "](" + escapeUrlDestination(url) + ")";
  }

  /** Like {@link #link} but the label is already-rendered Markdown, emitted verbatim. */
  public static String linkRendered(String label, String url) {
    return "[" + label + "](" + escapeUrlDestination(url) + ")";
  }

  /** Like {@link #linkRendered} with a sanitized, one-line, quote-escaped link title. */
  public static String linkRendered(String label, String url, String title) {
    return "[" + label + "](" + escapeUrlDestination(url) + " \"" + escapeLinkTitle(title) + "\")";
  }

  // A newline inside (... "title") would split the line and break the parse, so collapse breaks first.
  private static String escapeLinkTitle(String title) {
    return collapseLineBreaks(Objects.requireNonNullElse(title, ""))
        .replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * A fenced code block whose fence is long enough to survive any backtick run in {@code content}
   * (minimum three), with {@code language} as the info string when non-blank. Null content is treated
   * as empty.
   */
  public static String fencedCodeBlock(String content, String language) {
    var body = Objects.requireNonNullElse(content, "");
    var ticks = "`".repeat(Math.max(3, longestBacktickRun(body) + 1));
    // Info string must be one clean token: backticks/newlines would break the fence.
    var info = language == null ? "" : collapseLineBreaks(language.replace("`", "")).strip();
    var openingFence = (ticks + info).stripTrailing();
    return "%s\n%s\n%s".formatted(openingFence, body, ticks).stripTrailing();
  }

  /**
   * An inline code span whose fence exceeds the longest backtick run in {@code content}, padding a
   * space each side when the content borders a backtick (CommonMark strips one space per side). Null
   * content is treated as empty.
   */
  public static String inlineCodeSpan(String content) {
    var value = Objects.requireNonNullElse(content, "");
    var fence = "`".repeat(longestBacktickRun(value) + 1);
    var needsPadding = !value.isEmpty()
        && (value.charAt(0) == '`' || value.charAt(value.length() - 1) == '`');
    return needsPadding ? fence + " " + value + " " + fence : fence + value + fence;
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
   * and neutralises a leading block marker (#, &gt;, -, +, ordered "1." or "1)", indented-code run) on
   * each line — the first line only when {@code atLineStart}, every later line unconditionally. An
   * intra-word {@code _} (one flanked by word characters on both sides) is left literal, since
   * CommonMark never treats it as emphasis there. {@code (} and {@code )} are escaped only when
   * {@code escapeParentheses} is true (a leading "1)" marker is neutralised regardless). Null is
   * treated as empty.
   */
  public static String escapeInlineText(String text, boolean atLineStart, boolean escapeParentheses) {
    var value = Objects.requireNonNullElse(text, "");
    if (value.isEmpty()) {
      return value;
    }

    var escaped = escapeInlinePunctuation(value, escapeParentheses);
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
  // character is present (the common no-special-char case returns value unchanged). An intra-word
  // '_' is left literal — CommonMark never reads it as emphasis there.
  private static String escapeInlinePunctuation(String value, boolean escapeParentheses) {
    StringBuilder escaped = null;
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      var escape = c == '_'
          ? !isIntraWordUnderscore(value, i)
          : isInlinePunctuation(c, escapeParentheses);
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
    return i > 0 && isWordChar(value.charAt(i - 1))
        && i + 1 < value.length() && isWordChar(value.charAt(i + 1));
  }

  private static boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c);
  }

  private static boolean isInlinePunctuation(char c, boolean escapeParentheses) {
    return switch (c) {
      case '\\', '`', '*', '[', ']', '~', '<', '&', '!' -> true;
      // Parentheses only parse specially inside a link destination, so escaping them is opt-in.
      case '(', ')' -> escapeParentheses;
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
    // Ordered marker: escape whichever delimiter matched ("." or ")") so the digits can't open a list.
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

  /**
   * Backslash-escapes {@code [ ]} in image alt text, and {@code ( )} too when
   * {@code escapeParentheses} is set. Null is treated as empty.
   */
  public static String escapeAltText(String alt, boolean escapeParentheses) {
    var escaped = Objects.requireNonNullElse(alt, "").replace("[", "\\[").replace("]", "\\]");
    return escapeParentheses ? escaped.replace("(", "\\(").replace(")", "\\)") : escaped;
  }

  /**
   * Makes a URL safe inside a markdown {@code (...)} destination: a dangerous scheme
   * (javascript:, data:, …) is first defused, then the result is returned unchanged when clean,
   * wrapped as {@code <url>} when it holds a space/control char or unbalanced parentheses, or with
   * space/parens percent-encoded when angle-wrapping is unavailable. Null/blank is returned
   * unchanged.
   */
  public static String escapeUrlDestination(String url) {
    if (url == null || url.isBlank()) {
      return url;
    }

    url = sanitizeScheme(url);

    var hasAngleOrNewline = url.indexOf('<') >= 0 || url.indexOf('>') >= 0 || url.indexOf('\n') >= 0
        || url.indexOf('\r') >= 0;

    if (!hasAngleOrNewline) {
      if (hasSpaceOrControl(url) || hasUnbalancedParens(url)) {
        return "<" + url + ">";
      }
      return url;
    }

    // Angle-wrapping is unavailable (the URL holds '<'/'>'/newline), so percent-encode the
    // characters that would otherwise break the bare CommonMark destination.
    return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        .replace("<", "%3C").replace(">", "%3E").replace("\n", "%0A").replace("\r", "%0D");
  }

  // Defuse a non-safe scheme (javascript:, data:, …) by percent-encoding its colon; safe and relative
  // URLs pass through. Allocates only on the rare neutralise path.
  private static String sanitizeScheme(String url) {
    if (hasSafeOrNoScheme(url)) {
      return url;
    }
    // Drop the C0 controls/DEL a browser would ignore (the tab/newline tricks that smuggle a scheme
    // past a naive check), then defuse the colon.
    var cleaned = new StringBuilder(url.length());
    for (var i = 0; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c >= ' ' && c != 0x7f) {
        cleaned.append(c);
      }
    }
    var colon = cleaned.indexOf(":");
    if (colon >= 0) {
      cleaned.replace(colon, colon + 1, "%3A");
    }
    return cleaned.toString();
  }

  // True when url is relative (no scheme) or its scheme is in SAFE_URL_SCHEMES. An intra-scheme
  // tab/CR/LF — which a browser strips before re-reading the scheme — counts as unsafe.
  private static boolean hasSafeOrNoScheme(String url) {
    var i = 0;
    while (i < url.length() && url.charAt(i) <= ' ') {
      i++;
    }
    var start = i;
    var dirty = false;
    for (; i < url.length(); i++) {
      var c = url.charAt(i);
      if (c == ':') {
        return !dirty && i > start && isSafeScheme(url, start, i);
      }
      if (c == '\t' || c == '\n' || c == '\r') {
        dirty = true;
      } else if (!isSchemeChar(c, i == start)) {
        return true; // no scheme prefix before ':' → nothing to exploit
      }
    }
    return true;
  }

  private static boolean isSafeScheme(String url, int start, int end) {
    for (var scheme : SAFE_URL_SCHEMES) {
      if (scheme.length() == end - start
          && url.regionMatches(true, start, scheme, 0, scheme.length())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSchemeChar(char c, boolean first) {
    var letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    return letter || (!first && ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.'));
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
