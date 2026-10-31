package dev.nthings.adf4j.ast;

import java.util.List;

/// A forced line break (`hardBreak`) within a paragraph.
public record HardBreak(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public HardBreak {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "hardBreak";
  }
}
