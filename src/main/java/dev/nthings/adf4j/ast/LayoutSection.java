package dev.nthings.adf4j.ast;

import java.util.List;

public record LayoutSection(List<LayoutColumn> content) implements AdfBlock {

  public LayoutSection {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
