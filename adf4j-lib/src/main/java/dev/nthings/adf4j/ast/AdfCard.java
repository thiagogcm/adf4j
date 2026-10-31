package dev.nthings.adf4j.ast;

/// A smart-card node: {@link InlineCard}, {@link BlockCard} or {@link EmbedCard}.
public sealed interface AdfCard permits InlineCard, BlockCard, EmbedCard {

  /// The node's raw ADF attributes.
  Attributes attrs();

  /// The card's fields, read from `attrs`.
  default CardAttrs cardAttrs() {
    return CardAttrs.from(attrs());
  }
}
