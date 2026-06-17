package dev.nthings.adf4j.options;

import dev.nthings.adf4j.ast.Attributes;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// The data a custom {@link ExtensionRenderer} sees for one extension node: its type/key identity,
/// the optional fallback `text`, the flattened macro `parameters`, and the node's full raw
/// parameter envelope (`rawParameters`). For a bodied extension this is the header line only;
/// the engine still renders the body blocks.
///
/// `parameters` is the conventional `parameters.macroParams` value map most Confluence
/// macros use. `rawParameters` is everything under the node's `parameters` attribute as
/// plain values. Some extensions (e.g. the modern chart app, editor-migration macros) keep their
/// data outside `macroParams`, and this is the only place it surfaces.
public record ExtensionContext(
    @Nullable String extensionType,
    @Nullable String extensionKey,
    @Nullable String text,
    Map<String, String> parameters,
    Attributes rawParameters) {

  public ExtensionContext {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    rawParameters = rawParameters == null ? Attributes.empty() : rawParameters;
  }

  /// The macro parameter for `name`, or `null` when absent.
  public @Nullable String parameter(String name) {
    return parameters.get(name);
  }
}
