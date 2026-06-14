package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record TableCell(
    boolean header, int colspan, int rowspan, @Nullable String background, List<AdfBlock> content)
    implements AdfBlock {

  public TableCell {
    colspan = colspan < 1 ? 1 : colspan;
    rowspan = rowspan < 1 ? 1 : rowspan;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
