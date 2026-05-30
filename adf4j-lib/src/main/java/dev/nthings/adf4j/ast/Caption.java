package dev.nthings.adf4j.ast;

import java.util.List;

public record Caption(List<AdfInline> content) implements AdfBlock {

  public Caption {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
