package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

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
