package dev.nthings.adf4j.ast;

import java.util.List;

/// A `nestedExpand`: the {@link Expand} variant permitted inside other blocks (e.g. a table cell or
/// layout column), where a top-level `expand` is not. `title` is the toggle summary (empty when
/// none); `content` is the revealed blocks.
public record NestedExpand(String title, List<AdfBlock> content) implements AdfBlock {

  public NestedExpand {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
