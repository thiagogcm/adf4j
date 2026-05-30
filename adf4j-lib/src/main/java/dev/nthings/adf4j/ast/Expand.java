package dev.nthings.adf4j.ast;

import java.util.List;

public record Expand(String title, List<AdfBlock> content) implements AdfBlock {

  public Expand {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
