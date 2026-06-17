package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// The inline (in-the-flow-of-text) form of {@link Extension}, with the same identity and
/// parameter components (`extensionType`/`extensionKey`/`text`/`macroParams`/`parameters`), but
/// rendered within a line rather than as its own block. Custom rendering goes through the same
/// `ExtensionRenderer` hook.
public record InlineExtension(
    @Nullable String extensionType,
    @Nullable String extensionKey,
    @Nullable String text,
    MacroParams macroParams,
    Attributes parameters)
    implements AdfInline {

  public InlineExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    parameters = parameters == null ? Attributes.empty() : parameters;
  }
}
