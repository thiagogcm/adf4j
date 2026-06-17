package dev.nthings.adf4j.ast;

import java.util.List;

/// A `heading` block. `level` is the ADF heading level (1 to 6); out-of-range values are kept
/// as-is here and clamped to the 1 to 6 range only when rendered. `content` is the heading's inline
/// text; `marks` are block-level marks on the whole heading.
public record Heading(int level, List<AdfInline> content, List<AdfMark> marks) implements AdfBlock {

  public Heading {
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
