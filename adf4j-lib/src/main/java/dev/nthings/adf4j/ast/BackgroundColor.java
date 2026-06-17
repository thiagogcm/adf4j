package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Highlight (`backgroundColor`) mark. `color` is a CSS colour (e.g. `#fff0b3`). Visual-only and
/// dropped by default; emitted as a `<span style="background-color:…">` only when
/// `MarkdownOptions.htmlVisualMarks()` is enabled.
public record BackgroundColor(@Nullable String color) implements AdfMark {

  @Override
  public String type() {
    return "backgroundColor";
  }
}
