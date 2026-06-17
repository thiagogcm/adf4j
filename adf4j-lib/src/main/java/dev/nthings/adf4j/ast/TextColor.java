package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Foreground-colour (`textColor`) mark. `color` is a CSS colour (e.g. `#ff0000`). Visual-only and
/// dropped by default; emitted as a `<span style="color:…">` only when
/// `MarkdownOptions.htmlVisualMarks()` is enabled.
public record TextColor(@Nullable String color) implements AdfMark {

  @Override
  public String type() {
    return "textColor";
  }
}
