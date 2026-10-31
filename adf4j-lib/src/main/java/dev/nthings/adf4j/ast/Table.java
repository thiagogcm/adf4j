package dev.nthings.adf4j.ast;

import java.util.List;

/// A `table`: `content` is its {@link TableRow}s in top-to-bottom order. When `numberColumnEnabled`
/// is set, the source asked for a leading 1-based row-number column.
public record Table(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock,
        BodiedExtensionContent,
        BodiedSyncBlockContent,
        DocContent,
        ExpandContent,
        LayoutColumnContent {

  public Table {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "table";
  }

  public boolean numberColumnEnabled() {
    return attrs.bool("isNumberColumnEnabled", false);
  }

  /// The {@link TableRow} children; `content` keeps any other child.
  public List<TableRow> rows() {
    return ChildNodes.ofType(content, TableRow.class);
  }
}
