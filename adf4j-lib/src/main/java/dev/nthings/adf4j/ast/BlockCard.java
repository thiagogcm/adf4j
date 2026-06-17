package dev.nthings.adf4j.ast;

/// A block-level smart link: a URL or datasource rendered as its own block (a rich link card).
/// All of its data lives on {@link CardAttrs}: a `url`-backed card links out, a
/// `datasourceId`-backed card references a datasource by id. See {@link InlineCard} for the in-text
/// form and {@link EmbedCard} for the iframe-style embed.
public record BlockCard(CardAttrs attrs) implements AdfBlock {

  public BlockCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, Attributes.empty()) : attrs;
  }
}
