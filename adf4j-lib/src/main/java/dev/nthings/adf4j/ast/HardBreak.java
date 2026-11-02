package dev.nthings.adf4j.ast;

import java.util.List;

/// A forced line break (`hardBreak`) within a paragraph.
public record HardBreak(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public HardBreak {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "hardBreak";
  }
}
