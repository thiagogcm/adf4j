package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An `@`-mention. `text` (stripped) is the preferred rendering; when blank it falls back to
/// `@`+`id`, then `localId`, else `@unknown`.
public record Mention(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Mention {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "mention";
  }

  public @Nullable String id() {
    return attrs.text("id");
  }

  public String text() {
    return attrs.text("text", "").strip();
  }

  public @Nullable String userType() {
    return attrs.text("userType");
  }

  public @Nullable String accessLevel() {
    return attrs.text("accessLevel");
  }

  public @Nullable String localId() {
    return attrs.text("localId");
  }
}
