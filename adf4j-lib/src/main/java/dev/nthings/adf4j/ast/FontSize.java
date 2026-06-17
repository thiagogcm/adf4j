package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Font-size (`fontSize`) mark. `fontSize` is a CSS length used verbatim (e.g. `20px`).
/// Visual-only and dropped by default; emitted as a `<span style="font-size:…">` only when
/// `MarkdownOptions.htmlVisualMarks()` is enabled.
public record FontSize(@Nullable String fontSize) implements AdfMark {

  @Override
  public String type() {
    return "fontSize";
  }
}
