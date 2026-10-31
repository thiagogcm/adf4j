package dev.nthings.adf4j.ast;

/// Underline (`underline`) text mark; renders as a `<u>` HTML tag (Markdown has no underline).
public record Underline(Attributes attrs) implements AdfMark {

  public Underline {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "underline";
  }
}
