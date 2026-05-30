package dev.nthings.adf4j.ast;

import java.util.List;

public record DecisionList(List<DecisionItem> content) implements AdfBlock {

  public DecisionList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
