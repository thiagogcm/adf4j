package dev.nthings.adf4j.ast;

import java.util.List;

/// A column of a {@link LayoutSection}. `width` is its intended percentage of the row and is not
/// reflected in Markdown.
public record LayoutColumn(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public LayoutColumn {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "layoutColumn";
  }

  public double width() {
    return attrs.number("width", 0);
  }
}
