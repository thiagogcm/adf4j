package dev.nthings.adf4j.ast;

import java.util.List;

/// A `bulletList`: an unordered list whose `content` is its {@link ListItem} entries.
public record BulletList(List<ListItem> content) implements AdfBlock {

  public BulletList {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
