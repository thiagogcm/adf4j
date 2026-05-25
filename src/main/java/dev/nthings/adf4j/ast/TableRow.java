package dev.nthings.adf4j.ast;

import java.util.List;

public record TableRow(List<TableCell> content) implements AdfBlock {

  public TableRow {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
