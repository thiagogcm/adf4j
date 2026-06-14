package dev.nthings.adf4j.internal.analyze;

import dev.nthings.adf4j.ast.MacroParams;

import org.jspecify.annotations.Nullable;

/** The clamped 1..6 heading-level window a {@code toc} macro covers (min/max swapped if inverted). */
public record TocLevelRange(int min, int max) {

  public static TocLevelRange of(MacroParams macroParams) {
    var rawMin = parseIntOrDefault(macroParams.value("minLevel"), 1);
    var rawMax = parseIntOrDefault(macroParams.value("maxLevel"), 6);
    return new TocLevelRange(
        Math.clamp(Math.min(rawMin, rawMax), 1, 6), Math.clamp(Math.max(rawMin, rawMax), 1, 6));
  }

  public boolean includes(int level) {
    return level >= min && level <= max;
  }

  private static int parseIntOrDefault(@Nullable String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException _) {
      return fallback;
    }
  }
}
