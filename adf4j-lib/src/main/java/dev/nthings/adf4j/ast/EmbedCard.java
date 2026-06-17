package dev.nthings.adf4j.ast;

/// A block-level embedded smart link: the iframe-style preview of a URL (video, document, …).
/// Its data lives on {@link CardAttrs}, normally a `url`. Renders to a Markdown link when it has a
/// `url` or title, otherwise a placeholder label, since Markdown has no live embed. See
/// {@link BlockCard} for the plain card and {@link InlineCard} for the in-text form.
public record EmbedCard(CardAttrs attrs) implements AdfBlock {

  public EmbedCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, Attributes.empty()) : attrs;
  }
}
