package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A status lozenge. `text` is the label and the only rendered part, falling back to `"status"`
/// when blank. `color` and `style` are visual-only and dropped from Markdown.
public record Status(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Status {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "status";
  }

  public @Nullable String text() {
    return attrs.text("text");
  }

  public @Nullable String color() {
    return attrs.text("color");
  }

  public @Nullable String style() {
    return attrs.text("style");
  }

  public @Nullable String localId() {
    return attrs.text("localId");
  }
}
