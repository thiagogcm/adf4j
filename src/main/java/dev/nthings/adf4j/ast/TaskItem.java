package dev.nthings.adf4j.ast;

import java.util.List;

public record TaskItem(String state, List<AdfInline> content) implements AdfBlock {

  public TaskItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
