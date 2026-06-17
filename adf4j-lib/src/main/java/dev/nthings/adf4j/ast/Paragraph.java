package dev.nthings.adf4j.ast;

import java.util.List;

/// A `paragraph` block: a run of inline `content` (text and atoms). `marks` are block-level marks
/// applied to the whole paragraph (e.g. `alignment`, `indentation`), distinct from the inline marks
/// carried on each {@link Text}.
public record Paragraph(List<AdfInline> content, List<AdfMark> marks) implements AdfBlock {

  public Paragraph {
    content = content == null ? List.of() : List.copyOf(content);
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
