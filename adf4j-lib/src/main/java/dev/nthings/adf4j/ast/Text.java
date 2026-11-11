package dev.nthings.adf4j.ast;

import java.util.List;

/// A literal `text` run with inline `marks`. `marks` apply in the order an {@link AdfMark} renderer
/// dictates, not source order.
public record Text(String text, Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Text {
    marks = List.copyOf(marks);
  }

  /// A text run without attributes, the only form the ADF schema defines.
  public Text(String text, List<AdfMark> marks) {
    this(text, Attributes.empty(), marks);
  }

  @Override
  public String type() {
    return "text";
  }
}
