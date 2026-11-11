package dev.nthings.adf4j.ast;

/// Underline text mark; renders as `<u>` (Markdown has no underline).
public record Underline(Attributes attrs) implements TextMark {

  @Override
  public String type() {
    return "underline";
  }
}
