package dev.nthings.adf4j.ast;

import java.util.List;

/// A table whose `content` is its {@link TableRow}s, top to bottom. `numberColumnEnabled` asks for
/// a leading 1-based row-number column.
public record Table(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent {

  public Table {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "table";
  }

  public boolean numberColumnEnabled() {
    return attrs.bool("isNumberColumnEnabled", false);
  }

  /// The {@link TableRow} children; other children stay only in `content`.
  public List<TableRow> rows() {
    return ChildNodes.ofType(content, TableRow.class);
  }

  public Table withLayout(Adf.TableLayout layout) {
    return new Table(attrs.with("layout", layout.value()), content, marks);
  }

  public Table withWidth(int width) {
    return new Table(attrs.with("width", width), content, marks);
  }

  public Table withNumberColumn() {
    return new Table(attrs.with("isNumberColumnEnabled", true), content, marks);
  }
}
