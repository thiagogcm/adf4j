package dev.nthings.adf4j.ast;

public record InlineCard(CardAttrs attrs) implements AdfInline {

  public InlineCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, null) : attrs;
  }
}
