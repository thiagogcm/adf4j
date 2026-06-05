package dev.nthings.adf4j.extension;

import java.util.Map;

/**
 * The data a custom {@link ExtensionRenderer} sees for one extension node: its type/key identity, the
 * optional fallback {@code text}, and the macro {@code parameters}. For a bodied extension this is the
 * header line only; the engine still renders the body blocks.
 */
public record ExtensionContext(
    String extensionType, String extensionKey, String text, Map<String, String> parameters) {

  public ExtensionContext {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }

  /** The macro parameter for {@code name}, or {@code null} when absent. */
  public String parameter(String name) {
    return parameters.get(name);
  }
}
