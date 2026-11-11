package dev.nthings.adf4j.ast;

import java.util.List;

/// One entry of a {@link BulletList} or {@link OrderedList}; `content` holds its blocks.
public record ListItem(Attributes attrs, List<AdfBlock> content, List<AdfMark> marks)
    implements AdfBlock {

  public ListItem {
    content = List.copyOf(content);
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "listItem";
  }
}
