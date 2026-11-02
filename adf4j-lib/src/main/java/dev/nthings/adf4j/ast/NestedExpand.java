package dev.nthings.adf4j.ast;

import java.util.List;

/// A `nestedExpand`: the {@link Expand} variant permitted inside other blocks (e.g. a table cell or
/// layout column), where a top-level `expand` is not. `title` is the toggle summary (empty when
/// none); `content` is the revealed blocks.
public record NestedExpand(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, ExpandContent, TableCellContent {

  public NestedExpand {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "nestedExpand";
  }

  public String title() {
    return attrs.text("title", "");
  }
}
