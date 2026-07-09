package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Null-safe string helpers shared across the AST, analyze and render layers.
public final class Strings {

  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  private Strings() {}

  /// The stripped value, or `null` when it is null or blank.
  public static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    var stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /// The first non-blank candidate as given (not stripped), or `null` when all are blank.
  public static @Nullable String firstNonBlank(@Nullable String... candidates) {
    for (var candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return null;
  }

  /// The first non-blank candidate, stripped, or `null` when all are blank.
  public static @Nullable String firstNonBlankStripped(@Nullable String... candidates) {
    for (var candidate : candidates) {
      var stripped = trimToNull(candidate);
      if (stripped != null) {
        return stripped;
      }
    }
    return null;
  }

  /// Every whitespace run (including line breaks) replaced by a single space.
  public static String collapseWhitespace(String value) {
    return WHITESPACE_RUN.matcher(value).replaceAll(" ");
  }
}
