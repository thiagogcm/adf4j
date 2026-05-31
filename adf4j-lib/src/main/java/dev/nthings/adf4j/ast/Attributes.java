package dev.nthings.adf4j.ast;

import java.util.Map;

/**
 * Generic, immutable view over a node's raw ADF {@code attrs}. Holds only plain JSON-shaped values
 * (String, Long, Double, Boolean, nested {@link Map}/{@link List}, never null), so the core AST
 * stays free of any product-specific or JSON-library types. Product layers such as
 * {@code dev.nthings.adf4j.confluence} read their extras from here, which keeps the dependency
 * pointing from those layers to the AST and never the other way around.
 */
public record Attributes(Map<String, Object> values) {

  private static final Attributes EMPTY = new Attributes(Map.of());

  public Attributes {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  public static Attributes empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /** The value at {@code key} when it is a string, otherwise {@code null}. */
  public String string(String key) {
    return values.get(key) instanceof String value ? value : null;
  }

  /** The nested object at {@code key}, or {@link #empty()} when absent or not an object. */
  @SuppressWarnings("unchecked")
  public Attributes object(String key) {
    return values.get(key) instanceof Map<?, ?> nested
        ? new Attributes((Map<String, Object>) nested)
        : EMPTY;
  }
}
