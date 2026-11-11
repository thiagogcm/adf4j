package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An inline date pill. `timestamp` is a Unix epoch string, in seconds when small and otherwise
/// milliseconds; it renders as a UTC ISO date (`yyyy-MM-dd`). A null, blank or unparseable
/// `timestamp` yields no date, and a non-numeric raw value is kept. `localId` is not rendered.
public record Date(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Date {
    marks = List.copyOf(marks);
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
