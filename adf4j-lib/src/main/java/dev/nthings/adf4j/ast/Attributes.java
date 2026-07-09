package dev.nthings.adf4j.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Generic, immutable view over a node's raw ADF `attrs`, iterating in the source document's
/// attribute order. Holds only plain JSON-shaped values (String, Long, Double, Boolean, nested
/// `Map`/`List`); map keys and values are never null, but a nested list may contain null elements
/// so a JSON `null` keeps its array index. This keeps the core AST free of any product-specific
/// or JSON-library types. Product layers such as the `confluence` package read their extras from
/// here, which keeps the dependency pointing from those layers to the AST and never the other way
/// around.
public record Attributes(Map<String, Object> values) {

  private static final Attributes EMPTY = new Attributes(Map.of());

  public Attributes {
    values = orderedCopy(values);
  }

  // Not Map.copyOf: its per-JVM-run salted iteration order would lose the document order.
  private static Map<String, Object> orderedCopy(@Nullable Map<String, Object> values) {
    if (values == null || values.isEmpty()) {
      return Map.of();
    }
    var copy = LinkedHashMap.<String, Object>newLinkedHashMap(values.size());
    for (var entry : values.entrySet()) {
      copy.put(
          Objects.requireNonNull(entry.getKey(), "key"),
          Objects.requireNonNull(entry.getValue(), "value"));
    }
    return Collections.unmodifiableMap(copy);
  }

  public static Attributes empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /// The value at `key` when it is a string, otherwise `null`.
  public @Nullable String string(String key) {
    return values.get(key) instanceof String value ? value : null;
  }

  /// The nested object at `key`, or {@link #empty()} when absent or not an object.
  @SuppressWarnings("unchecked")
  public Attributes object(String key) {
    return values.get(key) instanceof Map<?, ?> nested
        ? new Attributes((Map<String, Object>) nested)
        : EMPTY;
  }
}
