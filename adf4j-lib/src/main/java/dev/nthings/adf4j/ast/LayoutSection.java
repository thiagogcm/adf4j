package dev.nthings.adf4j.ast;

import java.util.List;

/// A multi-column row; `content` holds its {@link LayoutColumn}s left to right.
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

  /// The {@link LayoutColumn} children; other children stay only in `content`.
  public List<LayoutColumn> columns() {
    return ChildNodes.ofType(content, LayoutColumn.class);
  }
}
