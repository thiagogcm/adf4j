package dev.nthings.adf4j.ast;

public record InlineExtension(
    String extensionType,
    String extensionKey,
    String text,
    MacroParams macroParams,
    Attributes parameters)
    implements AdfInline {

  public InlineExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    parameters = parameters == null ? Attributes.empty() : parameters;
  }
}
