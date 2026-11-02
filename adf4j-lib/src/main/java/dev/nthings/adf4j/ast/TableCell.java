package dev.nthings.adf4j.ast;

import java.util.Arrays;
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
    content = List.copyOf(content);
    marks = List.copyOf(marks);
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

  /// This cell spanning `colspan` columns.
  public TableCell withColspan(int colspan) {
    return new TableCell(header, attrs.with("colspan", colspan), content, marks);
  }

  /// This cell spanning `rowspan` rows.
  public TableCell withRowspan(int rowspan) {
    return new TableCell(header, attrs.with("rowspan", rowspan), content, marks);
  }

  /// This cell with its column widths in pixels, one for each column it spans.
  public TableCell withColwidth(int... widths) {
    var colwidth = Arrays.stream(widths).boxed().toList();
    return new TableCell(header, attrs.with("colwidth", colwidth), content, marks);
  }

  /// This cell with a background color, as `#rrggbb`.
  public TableCell withBackground(String color) {
    return new TableCell(header, attrs.with("background", color), content, marks);
  }
}
