package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Foreground colour text mark; `color` is a CSS colour. Dropped by default; emitted as a `<span>`
/// only when `ConversionOptions.htmlVisualMarks()` is on and the colour is a safe CSS value.
public record TextColor(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "textColor";
  }

  public @Nullable String color() {
    return attrs.text("color");
  }
}
