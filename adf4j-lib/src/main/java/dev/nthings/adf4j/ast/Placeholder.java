package dev.nthings.adf4j.ast;

import java.util.List;

/// Editor placeholder (ghost) text; `text` renders verbatim as inline text.
public record Placeholder(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Placeholder {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "placeholder";
  }

  public String text() {
    return attrs.text("text", "");
  }
}
