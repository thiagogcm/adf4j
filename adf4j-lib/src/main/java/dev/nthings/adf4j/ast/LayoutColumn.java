package dev.nthings.adf4j.ast;

import java.util.List;

/// A `layoutColumn` within a {@link LayoutSection}. `width` is the column's intended width as a
/// percentage of the row (not reflected in the Markdown output); `content` is the column's blocks.
public record LayoutColumn(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public LayoutColumn {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "layoutColumn";
  }

  public int width() {
    return attrs.integer("width", 0);
  }
}
