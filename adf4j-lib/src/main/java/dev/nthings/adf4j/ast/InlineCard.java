package dev.nthings.adf4j.ast;

/// An inline smart link: a URL rendered within the flow of text as a rich link chip. Its data
/// lives on {@link CardAttrs}, normally a `url`. See {@link BlockCard} and {@link EmbedCard} for
/// the block-level forms.
public record InlineCard(CardAttrs attrs) implements AdfInline {

  public InlineCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, Attributes.empty()) : attrs;
  }
}
