package dev.nthings.adf4j.ast;

/// Bold (`strong`) text mark; renders as `**`.
public record Strong(Attributes attrs) implements AdfMark {

  public Strong {
    attrs = attrs == null ? Attributes.empty() : attrs;
  }

  @Override
  public String type() {
    return "strong";
  }
}
