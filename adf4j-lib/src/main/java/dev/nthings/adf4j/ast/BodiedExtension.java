package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An {@link Extension} that wraps a single block body: the macro's own `content` blocks, which
/// the engine renders as normal even when a custom renderer handles the header. The identity and
/// parameter components (`extensionType`/`extensionKey`/`text`/`macroParams`/`parameters`) carry
/// the same meaning as on {@link Extension}; an `ExtensionRenderer` here produces the header line
/// only. For multiple bodies see {@link MultiBodiedExtension}.
public record BodiedExtension(
    @Nullable String extensionType,
    @Nullable String extensionKey,
    @Nullable String text,
    MacroParams macroParams,
    Attributes parameters,
    List<AdfBlock> content)
    implements AdfBlock {

  public BodiedExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    parameters = parameters == null ? Attributes.empty() : parameters;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
