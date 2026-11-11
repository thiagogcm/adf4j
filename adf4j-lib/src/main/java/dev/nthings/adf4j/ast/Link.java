package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A hyperlink mark wrapping its text in a Markdown link. A `null`/blank `href` leaves the text
/// unlinked. `attrs` holds metadata (e.g. an internal page reference) used to resolve the href.
public record Link(Attributes attrs) implements TextMark, MediaMark {

  @Override
  public String type() {
    return "link";
  }

  public @Nullable String href() {
    return attrs.text("href");
  }

  public @Nullable String title() {
    return attrs.text("title");
  }
}
