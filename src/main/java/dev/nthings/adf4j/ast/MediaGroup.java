package dev.nthings.adf4j.ast;

import java.util.List;

public record MediaGroup(List<AdfBlock> content) implements AdfBlock {

  public MediaGroup {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
