package dev.nthings.adf4j.ast;

import java.util.List;

/// An `orderedList`: a numbered list of {@link ListItem} entries. `order` is the starting number
/// of the first item (`1` for a plain list, e.g. `5` to begin at 5).
public record OrderedList(int order, List<ListItem> content) implements AdfBlock {

  public OrderedList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
