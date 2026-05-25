package dev.nthings.adf4j.ast;

import java.util.List;

public record TaskList(List<AdfBlock> content) implements AdfBlock {

  public TaskList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
