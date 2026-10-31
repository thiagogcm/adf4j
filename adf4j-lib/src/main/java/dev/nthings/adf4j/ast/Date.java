package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An inline date pill. `timestamp` is a Unix epoch value as a string (interpreted as seconds when
/// small, otherwise milliseconds) and renders as a UTC ISO date (`yyyy-MM-dd`). A `null`, blank,
/// or unparseable `timestamp` yields no date (the raw value is kept if non-numeric).
/// `localId` is an editor-local identity, not part of the rendered output.
public record Date(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Date {
    attrs = attrs == null ? Attributes.empty() : attrs;
    marks = marks == null ? List.of() : List.copyOf(marks);
  }

  @Override
  public String type() {
    return "date";
  }

  public @Nullable String timestamp() {
    return attrs.text("timestamp");
  }

  public @Nullable String localId() {
    return attrs.text("localId");
  }
}
