package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

public record Extension(
    @Nullable String extensionType,
    @Nullable String extensionKey,
    @Nullable String text,
    MacroParams macroParams,
    Attributes parameters)
    implements AdfBlock {

  public Extension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    parameters = parameters == null ? Attributes.empty() : parameters;
  }
}
