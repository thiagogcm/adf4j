package dev.nthings.adf4j.ast;

import java.util.Map;

public record MacroParams(Map<String, String> values) {

  private static final MacroParams EMPTY = new MacroParams(Map.of());

  public MacroParams {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  public static MacroParams empty() {
    return EMPTY;
  }

  public String value(String key) {
    return values.get(key);
  }
}
