package dev.nthings.adf4j.ast;

public record Extension(String extensionType, String extensionKey, MacroParams macroParams)
    implements AdfBlock {

  public Extension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
  }
}
