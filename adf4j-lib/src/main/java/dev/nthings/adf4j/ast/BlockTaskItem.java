package dev.nthings.adf4j.ast;

import java.util.List;

public record BlockTaskItem(String state, List<AdfBlock> content) implements AdfBlock {

  public BlockTaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
