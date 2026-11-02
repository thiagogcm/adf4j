package dev.nthings.adf4j.ast;

/// Bold (`strong`) text mark; renders as `**`.
public record Strong(Attributes attrs) implements AdfMark {

  @Override
  public String type() {
    return "strong";
  }
}
