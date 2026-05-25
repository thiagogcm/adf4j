package dev.nthings.adf4j.ast;

import java.util.List;

public record Table(boolean numberColumnEnabled, List<TableRow> content) implements AdfBlock {

  public Table {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
