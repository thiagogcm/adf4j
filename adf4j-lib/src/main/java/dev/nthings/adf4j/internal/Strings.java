package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Null-safe string helpers.
public final class Strings {

  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
  private static final Pattern LINE_BREAK = Pattern.compile("\\R");

  private Strings() {}

  /// Stripped, or `null` when null or blank.
  public static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    var stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /// First non-blank candidate, unstripped.
  public static @Nullable String firstNonBlank(@Nullable String... candidates) {
    for (var candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  public static @Nullable String firstNonBlankStripped(@Nullable String... candidates) {
    for (var candidate : candidates) {
      var stripped = trimToNull(candidate);
      if (stripped != null) {
        return stripped;
      }
    }
    return null;
  }

  public static String collapseLineBreaks(String value) {
    return LINE_BREAK.matcher(value).replaceAll(" ");
  }

  public static String collapseWhitespace(String value) {
    return WHITESPACE_RUN.matcher(value).replaceAll(" ");
  }

  /// RFC 6901 token escaping; `~` must be escaped before `/`.
  public static String pointerToken(String value) {
    return value.replace("~", "~0").replace("/", "~1");
  }
}
