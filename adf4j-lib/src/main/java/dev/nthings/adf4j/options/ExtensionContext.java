package dev.nthings.adf4j.options;

import java.util.Map;

import dev.nthings.adf4j.ast.Attributes;

/**
 * The data a custom {@link ExtensionRenderer} sees for one extension node: its type/key identity, the
 * optional fallback {@code text}, the flattened macro {@code parameters}, and the node's full raw
 * parameter envelope ({@code rawParameters}). For a bodied extension this is the header line only;
 * the engine still renders the body blocks.
 *
 * <p>{@code parameters} is the conventional {@code parameters.macroParams} value map most Confluence
 * macros use. {@code rawParameters} is everything under the node's {@code parameters} attribute as
 * plain values — some extensions (e.g. the modern chart app, editor-migration macros) keep their data
 * outside {@code macroParams}, and this is the only place it surfaces.
 */
public record ExtensionContext(
    String extensionType,
    String extensionKey,
    String text,
    Map<String, String> parameters,
    Attributes rawParameters) {

  public ExtensionContext {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    rawParameters = rawParameters == null ? Attributes.empty() : rawParameters;
  }

  /** The macro parameter for {@code name}, or {@code null} when absent. */
  public String parameter(String name) {
    return parameters.get(name);
  }
}
