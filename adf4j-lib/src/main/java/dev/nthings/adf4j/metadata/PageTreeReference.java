package dev.nthings.adf4j.metadata;

import java.util.Map;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/// A `pagetree` or `children` macro occurrence: a metadata fact (the document depends on the page
/// hierarchy) and the request a {@link PageTreeResolver} expands. `root` is the trimmed root page
/// parameter, or null when absent, blank, or an `@keyword` such as `@self` (the current page). The
/// raw value stays in `parameters`; {@link #depth()} and {@link #all()} are pre-parsed from it.
public record PageTreeReference(
    PageTreeMacro macro, @Nullable String root, Map<String, String> parameters) {

  public PageTreeReference {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  /// The `depth` parameter as a positive count; empty if absent, blank, non-numeric or not
  /// positive.
  public OptionalInt depth() {
    var raw = parameters.get("depth");
    if (raw == null || raw.isBlank()) {
      return OptionalInt.empty();
    }
    try {
      var parsed = Integer.parseInt(raw.strip());
      return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
    } catch (NumberFormatException _) {
      return OptionalInt.empty();
    }
  }

  /// Whether `all` (or legacy `allChildren`, when `all` is absent or blank) is `true`, any case.
  public boolean all() {
    var raw = parameters.get("all");
    if (raw == null || raw.isBlank()) {
      raw = parameters.get("allChildren");
    }
    return raw != null && "true".equalsIgnoreCase(raw.strip());
  }
}
