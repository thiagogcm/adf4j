package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// Font-size (`fontSize`) mark. `fontSize` is a CSS size (e.g. `20px`). Visual-only and dropped by
/// default; emitted as a `<span style="font-size:…">` only when
/// `ConversionOptions.htmlVisualMarks()` is enabled and the value is of a safe CSS shape.
public record FontSize(Attributes attrs) implements ParagraphMark {

  @Override
  public String type() {
    return "fontSize";
  }

  public @Nullable String fontSize() {
    return attrs.text("fontSize");
  }
}
