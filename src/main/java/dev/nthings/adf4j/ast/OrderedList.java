package dev.nthings.adf4j.ast;

import java.util.List;

public record OrderedList(int order, List<ListItem> content) implements AdfBlock {

  public OrderedList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
