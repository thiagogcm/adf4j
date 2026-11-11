package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Foreground-colour (`textColor`) mark. `color` is a CSS colour (e.g. `#ff0000`). Visual-only and
/// dropped by default; emitted as a `<span style="color:…">` only when
/// `ConversionOptions.htmlVisualMarks()` is enabled and the value is of a safe CSS shape.
public record TextColor(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "textColor";
  }

  public @Nullable String color() {
    return attrs.text("color");
  }
}
