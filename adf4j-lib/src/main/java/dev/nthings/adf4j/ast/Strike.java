package dev.nthings.adf4j.ast;

/// Strikethrough (`strike`) text mark; renders as `~~`.
public record Strike(Attributes attrs) implements AdfMark {

  public Strike {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "strike";
  }
}
