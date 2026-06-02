package dev.nthings.adf4j.ast;

public record InlineExtension(
    String extensionType, String extensionKey, String text, MacroParams macroParams)
    implements AdfInline {

  public InlineExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
  }
}
