package dev.nthings.adf4j.ast;

/// Italic (`em`) text mark; renders as `*`.
public record Em(Attributes attrs) implements AdfMark {

  @Override
  public String type() {
    return "em";
  }
}
