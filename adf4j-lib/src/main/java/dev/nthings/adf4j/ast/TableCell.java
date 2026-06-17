package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A table cell: `tableHeader` when `header` is set, otherwise `tableCell`. `colspan`/`rowspan` are
/// the spanned column/row counts, each clamped to a minimum of 1. `background` is the cell's
/// background colour (e.g. a `#rrggbb` string), or `null` when none was set. `content` holds the
/// cell's blocks.
public record TableCell(
    boolean header, int colspan, int rowspan, @Nullable String background, List<AdfBlock> content)
    implements AdfBlock {

  public TableCell {
    colspan = colspan < 1 ? 1 : colspan;
    rowspan = rowspan < 1 ? 1 : rowspan;
    content = content == null ? List.of() : List.copyOf(content);
  }
}
