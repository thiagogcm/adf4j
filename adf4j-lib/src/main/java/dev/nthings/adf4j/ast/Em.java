package dev.nthings.adf4j.ast;

/// Italic (`em`) text mark; renders as `*`.
public record Em(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "em";
  }
}
