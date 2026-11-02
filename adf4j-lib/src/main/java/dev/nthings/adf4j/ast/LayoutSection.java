package dev.nthings.adf4j.ast;

import java.util.List;

/// A `layoutSection`: a multi-column row whose `content` is its {@link LayoutColumn}s in left-to-
/// right order.
public record LayoutSection(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock, BodiedSyncBlockContent, DocContent {

  public LayoutSection {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "layoutSection";
  }

  /// The {@link LayoutColumn} children; `content` keeps any other child.
  public List<LayoutColumn> columns() {
    return ChildNodes.ofType(content, LayoutColumn.class);
  }
}
