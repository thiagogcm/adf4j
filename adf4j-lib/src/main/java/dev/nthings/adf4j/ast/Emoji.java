package dev.nthings.adf4j.ast;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// An inline emoji. `text` is the rendered glyph (e.g. the Unicode character) and `shortName` its
/// colon code (e.g. `:smile:`); rendering prefers `text`, falling back to `shortName`. `id` is the
/// emoji provider's identifier.
public record Emoji(Attributes attrs, List<AdfMark> marks)
    implements AdfInline, CaptionContent, InlineContent {

  public Emoji {
    marks = List.copyOf(marks);
  }

  @Override
  public String type() {
    return "emoji";
  }

  public @Nullable String id() {
    return attrs.text("id");
  }

  public @Nullable String text() {
    return attrs.text("text");
  }

  public @Nullable String shortName() {
    return attrs.text("shortName");
  }
}
