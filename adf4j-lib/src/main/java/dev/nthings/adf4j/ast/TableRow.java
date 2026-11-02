package dev.nthings.adf4j.ast;

import java.util.List;

/// A `tableRow`: `content` is its {@link TableCell}s left to right. A cell may be a header or data
/// cell; the row does not distinguish the two (see {@link TableCell#header()}).
public record TableRow(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public TableRow {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "tableRow";
  }

  /// The {@link TableCell} children; `content` keeps any other child.
  public List<TableCell> cells() {
    return ChildNodes.ofType(content, TableCell.class);
  }
}
