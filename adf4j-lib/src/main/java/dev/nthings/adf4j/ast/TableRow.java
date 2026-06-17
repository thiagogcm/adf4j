package dev.nthings.adf4j.ast;

import java.util.List;

/// A `tableRow`: `content` is its {@link TableCell}s left to right. A cell may be a header or data
/// cell; the row does not distinguish the two (see {@link TableCell#header()}).
public record TableRow(List<TableCell> content) implements AdfBlock {

  public TableRow {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
