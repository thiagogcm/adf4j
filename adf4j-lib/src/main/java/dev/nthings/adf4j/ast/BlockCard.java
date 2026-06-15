package dev.nthings.adf4j.ast;

public record BlockCard(CardAttrs attrs) implements AdfBlock {

  public BlockCard {
    attrs = attrs == null ? new CardAttrs(null, null, null, null, Attributes.empty()) : attrs;
  }
}
