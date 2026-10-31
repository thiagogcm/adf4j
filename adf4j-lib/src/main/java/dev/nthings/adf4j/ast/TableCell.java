package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A table cell: `tableHeader` when `header` is set, otherwise `tableCell`. `colspan`/`rowspan` are
/// the spanned column/row counts, each clamped to a minimum of 1. `background` is the cell's
/// background colour (e.g. a `#rrggbb` string), or `null` when none was set. `content` holds the
/// cell's blocks.
public record TableCell(
    boolean header, Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public TableCell {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return header ? "tableHeader" : "tableCell";
  }

  public int colspan() {
    return Math.max(1, attrs.integer("colspan", 1));
  }

  public int rowspan() {
    return Math.max(1, attrs.integer("rowspan", 1));
  }

  public @Nullable String background() {
    return attrs.text("background");
  }
}
