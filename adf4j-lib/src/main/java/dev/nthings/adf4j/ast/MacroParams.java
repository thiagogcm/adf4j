package dev.nthings.adf4j.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// A macro's flattened parameter map (each ADF `macroParams` entry's `value`), iterating in the
/// source document's parameter order and keeping the AST independent of the raw parameter
/// envelope.
public record MacroParams(Map<String, String> values) {

  private static final MacroParams EMPTY = new MacroParams(Map.of());

  public MacroParams {
    // Not Map.copyOf: its per-JVM-run salted iteration order would lose the document order.
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static MacroParams empty() {
    return EMPTY;
  }

  /// Flattens a raw `macroParams` object: each entry's scalar, or the scalar in its
  /// `{"value": scalar}` envelope, as text. Non-scalars are dropped.
  public static MacroParams from(Attributes macroParams) {
    var values = new LinkedHashMap<String, String>();
    for (var key : macroParams.values().keySet()) {
      var scalar = macroParams.text(key);
      var resolved = scalar != null ? scalar : macroParams.object(key).text("value");
      if (resolved != null) {
        values.put(key, resolved);
      }
    }
    return new MacroParams(values);
  }

  public @Nullable String value(String key) {
    return values.get(key);
  }
}
