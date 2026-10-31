package dev.nthings.adf4j.ast;

import java.util.List;

/// A run of literal `text` carrying its inline `marks` (formatting and links). `marks` apply in
/// the order an {@link AdfMark} renderer dictates, not source order.
public record Text(String text, Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Text {
    text = text == null ? "" : text;
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  /// A text run without attributes, which is the only form the ADF schema defines.
  public Text(String text, List<AdfMark> marks) {
    this(text, Attributes.empty(), marks);
  }

  @Override
  public String type() {
    return "text";
  }
}
