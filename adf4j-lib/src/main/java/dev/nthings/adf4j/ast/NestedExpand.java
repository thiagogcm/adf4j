package dev.nthings.adf4j.ast;

import java.util.List;

public record NestedExpand(String title, List<AdfBlock> content) implements AdfBlock {

  public NestedExpand {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
