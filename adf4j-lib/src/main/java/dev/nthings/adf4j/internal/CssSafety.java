package dev.nthings.adf4j.internal;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Allowlist checks for CSS values extracted from untrusted ADF visual marks. Shared across
/// renderers so Markdown inline styles and HTML/CSS backends enforce the same safety boundary.
public final class CssSafety {

  // Colors and sizes only: none can end a declaration or attribute, or trigger external loads.
  private static final Pattern SAFE_CSS_VALUE =
      Pattern.compile(
          "#\\p{XDigit}{3,8}"
              + "|\\p{Alpha}[\\p{Alpha}-]*"
              + "|\\d+(?:\\.\\d+)?(?:\\p{Alpha}+|%)?"
              + "|(?:rgb|hsl)a?\\([\\d.,%/ -]*\\)");

  private static final Pattern CSS_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

  private CssSafety() {}

  /// Returns `value` trimmed when it matches the safe CSS value allowlist, else `null`.
  public static @Nullable String safeValue(@Nullable String value) {
    var stripped = Strings.trimToNull(value);
    return stripped != null && SAFE_CSS_VALUE.matcher(stripped).matches() ? stripped : null;
  }

  /// Returns `value` trimmed when it is a non-negative CSS number (for pixel widths), else `null`.
  public static @Nullable String safeNumber(@Nullable String value) {
    var stripped = Strings.trimToNull(value);
    return stripped != null && CSS_NUMBER.matcher(stripped).matches() ? stripped : null;
  }
}
