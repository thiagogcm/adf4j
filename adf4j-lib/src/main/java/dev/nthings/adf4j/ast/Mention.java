package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An `@`-mention of a user. `text` is the (stripped) display name and the preferred rendering;
/// when blank it falls back to `@`+`id` (then `localId`), else `@unknown`. `id` is the account id,
/// `userType` the kind of mentioned entity (e.g. `DEFAULT`, `SPECIAL`, `APP`), `accessLevel` the
/// mentioned user's container access (e.g. `CONTAINER`, `APPLICATION`). `localId` is editor-local.
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
