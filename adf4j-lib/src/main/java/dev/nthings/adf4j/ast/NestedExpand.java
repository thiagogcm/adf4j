package dev.nthings.adf4j.ast;

import java.util.List;

/// The {@link Expand} variant allowed inside other blocks (e.g. a table cell), where a top-level
/// `expand` is not. `title()` is empty when none is set.
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
