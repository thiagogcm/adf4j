package dev.nthings.adf4j.ast;

import java.util.List;

public record TableCell(
    boolean header, int colspan, int rowspan, String background, List<AdfBlock> content)
    implements AdfBlock {

  public TableCell {
    colspan = colspan < 1 ? 1 : colspan;
    rowspan = rowspan < 1 ? 1 : rowspan;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
