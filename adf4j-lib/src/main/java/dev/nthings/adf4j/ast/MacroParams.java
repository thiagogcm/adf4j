package dev.nthings.adf4j.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// A macro's flattened parameter map (each ADF `macroParams` entry's `value`), iterating in the
/// source document's parameter order and keeping the AST independent of the raw parameter
/// envelope.
public record MacroParams(Map<String, String> values) {

  private static final MacroParams EMPTY = new MacroParams(Map.of());

  public MacroParams {
    values = orderedCopy(values);
  }

  // Not Map.copyOf: its per-JVM-run salted iteration order would lose the document order.
  private static Map<String, String> orderedCopy(@Nullable Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return Map.of();
    }
    var copy = LinkedHashMap.<String, String>newLinkedHashMap(values.size());
    for (var entry : values.entrySet()) {
      copy.put(
          Objects.requireNonNull(entry.getKey(), "key"),
          Objects.requireNonNull(entry.getValue(), "value"));
    }
    return Collections.unmodifiableMap(copy);
  }

  public static MacroParams empty() {
    return EMPTY;
  }

  public @Nullable String value(String key) {
    return values.get(key);
  }
}
