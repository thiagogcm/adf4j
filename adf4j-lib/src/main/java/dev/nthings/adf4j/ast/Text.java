package dev.nthings.adf4j.ast;

import java.util.List;

/// A run of literal `text` carrying its inline `marks` (formatting and links). `marks` apply in
/// the order an {@link AdfMark} renderer dictates, not source order.
public record Text(String text, List<AdfMark> marks) implements AdfInline {

  public Text {
    text = text == null ? "" : text;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }
}
