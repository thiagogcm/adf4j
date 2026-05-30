package dev.nthings.adf4j.ast;

import java.util.List;

public record BodiedExtension(
    String extensionType, String extensionKey, MacroParams macroParams, List<AdfBlock> content)
    implements AdfBlock {

  public BodiedExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
