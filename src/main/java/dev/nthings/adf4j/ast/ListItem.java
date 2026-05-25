package dev.nthings.adf4j.ast;

import java.util.List;

public record ListItem(List<AdfBlock> content) implements AdfBlock {

  public ListItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
