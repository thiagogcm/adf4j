package dev.nthings.adf4j.ast;

import java.util.List;

public record MultiBodiedExtension(
    String extensionType,
    String extensionKey,
    String text,
    MacroParams macroParams,
    List<AdfBlock> content)
    implements AdfBlock {

  public MultiBodiedExtension {
    macroParams = macroParams == null ? MacroParams.empty() : macroParams;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
