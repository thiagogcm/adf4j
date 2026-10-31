package dev.nthings.adf4j.ast;

/// Italic (`em`) text mark; renders as `*`.
public record Em(Attributes attrs) implements AdfMark {

  public Em {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "em";
  }
}
