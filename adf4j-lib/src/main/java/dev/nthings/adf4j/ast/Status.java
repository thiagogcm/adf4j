package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// A status lozenge (the coloured pill). `text` is the label and the only rendered part (it falls
/// back to `"status"` when blank); `color` is the lozenge colour (e.g. `neutral`, `green`,
/// `yellow`, `red`, `blue`) and `style` its visual variant (e.g. `""`/`bold`). Both are
/// visual-only and dropped from Markdown. `localId` is editor-local.
public record Status(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Status {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
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
