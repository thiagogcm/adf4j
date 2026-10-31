package dev.nthings.adf4j.ast;

import java.util.List;

/// A `listItem`: one entry of a {@link BulletList} or {@link OrderedList}. `content` holds the
/// item's blocks (typically a {@link Paragraph}, optionally followed by nested lists).
public record ListItem(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public ListItem {
    attrs = attrs == null ? Attributes.empty() : attrs;
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "listItem";
  }
}
