package dev.nthings.adf4j.ast;

import java.util.List;

/// A `table`: `content` is its {@link TableRow}s in top-to-bottom order. When `numberColumnEnabled`
/// is set, the source asked for a leading 1-based row-number column.
public record Table(boolean numberColumnEnabled, List<TableRow> content) implements AdfBlock {

  public Table {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
