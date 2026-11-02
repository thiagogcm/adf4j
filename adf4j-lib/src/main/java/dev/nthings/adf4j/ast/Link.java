package dev.nthings.adf4j.ast;

import org.jspecify.annotations.Nullable;

/// A hyperlink (`link`) mark wrapping its text in a Markdown link. `href` is the destination; a
/// `null`/blank `href` leaves the text unlinked. `title` becomes the link title when present. The
/// surviving ADF attributes (`attrs`) carry the link metadata (e.g. an internal Confluence page
/// reference) used to resolve the href; see {@link Attributes}.
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
