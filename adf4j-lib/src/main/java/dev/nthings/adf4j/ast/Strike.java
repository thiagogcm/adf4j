package dev.nthings.adf4j.ast;

/// Strikethrough (`strike`) text mark; renders as `~~`.
public record Strike(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "strike";
  }
}
