package dev.nthings.adf4j.internal.render;

import dev.nthings.adf4j.ast.MacroParams;

/** The clamped 1..6 heading-level window a {@code toc} macro covers (min/max swapped if inverted). */
record TocLevelRange(int min, int max) {

  static TocLevelRange of(MacroParams macroParams) {
    var rawMin = parseIntOrDefault(macroParams.value("minLevel"), 1);
    var rawMax = parseIntOrDefault(macroParams.value("maxLevel"), 6);
    return new TocLevelRange(
        Math.clamp(Math.min(rawMin, rawMax), 1, 6), Math.clamp(Math.max(rawMin, rawMax), 1, 6));
  }

  boolean includes(int level) {
    return level >= min && level <= max;
  }

  private static int parseIntOrDefault(String raw, int fallback) {
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
