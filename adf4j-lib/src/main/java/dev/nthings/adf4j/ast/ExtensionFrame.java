package dev.nthings.adf4j.ast;

import java.util.List;

public record ExtensionFrame(List<AdfBlock> content) implements AdfBlock {

  public ExtensionFrame {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
