package dev.nthings.adf4j.ast;

import java.util.List;

/// Editor placeholder prompt text (the ghost text shown in an empty field); `text` renders
/// verbatim as inline text.
public record Placeholder(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Placeholder {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "placeholder";
  }

  public String text() {
    return attrs.text("text", "");
  }
}
