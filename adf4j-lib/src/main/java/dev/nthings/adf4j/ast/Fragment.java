package dev.nthings.adf4j.ast;

/// A fragment (`fragment`) mark grouping nodes into a named, reusable section. It has no visible
/// formatting and is dropped from the output, leaving the content intact.
public record Fragment(Attributes attrs) implements AdfMark {

  @Override
  public String type() {
    return "fragment";
  }
}
