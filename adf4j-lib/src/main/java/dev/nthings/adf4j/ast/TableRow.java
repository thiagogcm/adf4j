package dev.nthings.adf4j.ast;

import java.util.List;

/// A row: `content` is its {@link TableCell}s, left to right. The row does not distinguish header
/// from data cells (see {@link TableCell#header()}).
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

  /// The {@link TableCell} children; other children stay only in `content`.
  public List<TableCell> cells() {
    return ChildNodes.ofType(content, TableCell.class);
  }
}
