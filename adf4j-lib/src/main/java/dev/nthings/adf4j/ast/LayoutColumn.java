package dev.nthings.adf4j.ast;

import java.util.List;

/// A `layoutColumn` within a {@link LayoutSection}. `width` is the column's intended width as a
/// percentage of the row (not reflected in the Markdown output); `content` is the column's blocks.
public record LayoutColumn(int width, List<AdfBlock> content) implements AdfBlock {

  public LayoutColumn {
    content = content == null ? List.of() : List.copyOf(content);
  }
}
