package dev.nthings.adf4j.internal.analyze;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The only heading slug algorithm: GFM-style slugs (lowercased, spaces to `-`, other
/// punctuation dropped, `-n` suffix for repeats, `"section"` when nothing is left).
final class HeadingSlugger {

  private static final String DEFAULT_ID = "section";
  private static final Pattern ALLOWED =
      Pattern.compile("[\\w\\-_]+", Pattern.UNICODE_CHARACTER_CLASS);

  private final Map<String, Integer> counts = new HashMap<>();

  /// Returns a unique slug for `text` that does not collide with `taken`, adding it to `taken`.
  String uniqueSlug(String text, Set<String> taken) {
    var normalized = normalize(text);
    var base = normalized.isEmpty() ? DEFAULT_ID : normalized;
    var slug = nextSlug(base);
    while (!taken.add(slug)) {
      slug = nextSlug(base);
    }
    return slug;
  }

  private String nextSlug(String base) {
    var count = counts.get(base);
    if (count == null) {
      counts.put(base, 1);
      return base;
    }
    counts.put(base, count + 1);
    return base + "-" + count;
  }

  private static String normalize(String text) {
    var lowered = text.toLowerCase(Locale.ROOT).replace(" ", "-");
    var matcher = ALLOWED.matcher(lowered);
    var result = new StringBuilder(lowered.length());
    while (matcher.find()) {
      result.append(lowered, matcher.start(), matcher.end());
    }
    return result.toString();
  }
}
