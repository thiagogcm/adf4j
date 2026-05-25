package dev.nthings.adf4j.ast;

public record EmbedCard(CardAttrs attrs) implements AdfBlock {

  public EmbedCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, null) : attrs;
  }
}
