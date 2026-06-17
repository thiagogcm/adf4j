package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An extension whose body is split across several frames: its `content` is a list of
/// {@link ExtensionFrame} blocks, one per body region (e.g. a tabbed/multi-pane macro). The
/// identity and parameter components carry the same meaning as on
/// {@link Extension}/{@link BodiedExtension}; each frame's own blocks live inside its
/// {@link ExtensionFrame}.
public record MultiBodiedExtension(
    @Nullable String extensionType,
    @Nullable String extensionKey,
    @Nullable String text,
    MacroParams macroParams,
    Attributes parameters,
    List<AdfBlock> content)
    implements AdfBlock {

  public MultiBodiedExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    parameters = parameters == null ? Attributes.empty() : parameters;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
