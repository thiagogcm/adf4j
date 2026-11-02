package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Highlight (`backgroundColor`) mark. `color` is a CSS colour (e.g. `#fff0b3`). Visual-only and
/// dropped by default; emitted as a `<span style="background-color:…">` only when
/// `MarkdownOptions.htmlVisualMarks()` is enabled and the value is of a safe CSS shape.
public record BackgroundColor(Attributes attrs) implements AdfMark {

  public BackgroundColor {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "backgroundColor";
  }

  public @Nullable String color() {
    return attrs.text("color");
  }
}
