package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Text-alignment (`alignment`) mark. `align` is the alignment (e.g. `center`, `end`). Dropped by
/// default; a `center`/`end` value wraps the block in a `<div align>` only when
/// `MarkdownOptions.htmlVisualMarks()` is enabled.
public record Alignment(@Nullable String align) implements AdfMark {

  @Override
  public String type() {
    return "alignment";
  }
}
