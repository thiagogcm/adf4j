package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Border (`border`) mark around text/media. `color` is a CSS colour and `size` a pixel width
/// (e.g. `2`). Visual-only and dropped by default; emitted as a `<span style="border:…">` only
/// when `MarkdownOptions.htmlVisualMarks()` is enabled, and only when both `color` and `size` are
/// present.
public record Border(Attributes attrs) implements AdfMark {

  public Border {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "border";
  }

  public @Nullable String color() {
    return attrs.text("color");
  }

  public @Nullable String size() {
    return attrs.text("size");
  }
}
