package dev.nthings.adf4j.ast;

import java.util.List;

public record Panel(String panelType, List<AdfBlock> content) implements AdfBlock {

  public Panel {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
