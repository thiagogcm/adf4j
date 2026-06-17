package dev.nthings.adf4j.ast;

/// Underline (`underline`) text mark; renders as a `<u>` HTML tag (Markdown has no underline).
public record Underline() implements AdfMark {

  @Override
  public String type() {
    return "underline";
  }
}
