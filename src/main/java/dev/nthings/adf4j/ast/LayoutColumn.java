package dev.nthings.adf4j.ast;

import java.util.List;

public record LayoutColumn(int width, List<AdfBlock> content) implements AdfBlock {

  public LayoutColumn {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
