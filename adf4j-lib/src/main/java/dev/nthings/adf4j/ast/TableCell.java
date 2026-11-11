package dev.nthings.adf4j.ast;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A cell: `tableHeader` when `header` is set, otherwise `tableCell`. `colspan`/`rowspan` are
/// clamped to at least 1. `background` is a colour such as `#rrggbb`, or `null` when unset.
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

  public TableCell withColspan(int colspan) {
    return new TableCell(header, attrs.with("colspan", colspan), content, marks);
  }

  public TableCell withRowspan(int rowspan) {
    return new TableCell(header, attrs.with("rowspan", rowspan), content, marks);
  }

  /// This cell with column widths in pixels, one per spanned column.
  public TableCell withColwidth(int... widths) {
    var colwidth = Arrays.stream(widths).boxed().toList();
    return new TableCell(header, attrs.with("colwidth", colwidth), content, marks);
  }

  /// Sets `background` as a `#rrggbb` colour.
  public TableCell withBackground(String color) {
    return new TableCell(header, attrs.with("background", color), content, marks);
  }
}
