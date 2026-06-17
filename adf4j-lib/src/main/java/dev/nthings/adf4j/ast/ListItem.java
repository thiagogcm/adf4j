package dev.nthings.adf4j.ast;

import java.util.List;

/// A `listItem`: one entry of a {@link BulletList} or {@link OrderedList}. `content` holds the
/// item's blocks (typically a {@link Paragraph}, optionally followed by nested lists).
public record ListItem(List<AdfBlock> content) implements AdfBlock {

  public ListItem {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
