package dev.nthings.adf4j.ast;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/// A macro's flattened parameter map (each ADF `macroParams` entry's `value`), keeping the
/// AST independent of the raw parameter envelope.
public record MacroParams(Map<String, String> values) {

  private static final MacroParams EMPTY = new MacroParams(Map.of());

  public MacroParams {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  public static MacroParams empty() {
    return EMPTY;
  }

  public @Nullable String value(String key) {
    return values.get(key);
  }
}
