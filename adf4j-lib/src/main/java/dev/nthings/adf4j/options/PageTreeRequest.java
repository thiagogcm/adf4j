package dev.nthings.adf4j.options;

import java.util.Map;
import java.util.OptionalInt;

/**
 * The context passed to a {@link PageTreeResolver}. {@code macro} says which macro triggered the call
 * (their defaults differ — see {@link PageTreeMacro}). {@code root} is the page the list is rooted at
 * — the {@code pagetree} macro's {@code root} parameter or the {@code children} macro's {@code page}
 * parameter, trimmed — or {@code null} when it is absent, blank, or an {@code @keyword} (e.g.
 * {@code @self}); the raw value stays reachable via {@code parameters}. {@code parameters} is the
 * macro's full, unmodifiable parameter map (e.g. {@code depth}, {@code all}, {@code startDepth}).
 * The standard {@code depth} and {@code all} parameters are also exposed pre-parsed via
 * {@link #depth()} and {@link #all()}.
 */
public record PageTreeRequest(PageTreeMacro macro, String root, Map<String, String> parameters) {

  public PageTreeRequest {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  /**
   * The macro's {@code depth} parameter as a positive level count, or empty when it is absent, blank,
   * non-numeric, or not positive.
   */
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

  /**
   * Whether the macro asks for the whole subtree: the {@code all} parameter (falling back to its
   * legacy {@code allChildren} spelling when {@code all} is absent or blank) equals {@code "true"},
   * case-insensitively.
   */
  public boolean all() {
    var raw = parameters.get("all");
    if (raw == null || raw.isBlank()) {
      raw = parameters.get("allChildren");
    }
    return raw != null && "true".equalsIgnoreCase(raw.strip());
  }
}
