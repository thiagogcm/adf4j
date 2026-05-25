package dev.nthings.adf4j.ast;

import java.util.List;

public record DecisionItem(String state, List<AdfInline> content) implements AdfBlock {

  public DecisionItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
